-- Run once in Supabase SQL Editor. Additive: earlier v1 tables remain untouched.
begin;
create extension if not exists pgcrypto with schema extensions;
create schema if not exists vueo_private;
revoke all on schema vueo_private from public, anon, authenticated;

create table if not exists vueo_private.account_keys (
  user_id uuid primary key references auth.users(id) on delete cascade,
  secret text not null default encode(extensions.gen_random_bytes(32), 'hex')
);
create sequence if not exists vueo_private.changes;
create table if not exists vueo_private.records (
  user_id uuid not null references auth.users(id) on delete cascade,
  kind text not null check (kind in ('profiles','favorites','progress','history','addons','catalogs','integrations','trakt')),
  record_key text not null check (length(record_key) between 1 and 2048),
  encrypted_value bytea,
  deleted boolean not null default false,
  revision bigint not null default 0,
  updated_at timestamptz not null default now(),
  change_id bigint not null default nextval('vueo_private.changes'),
  lease_id uuid,
  lease_until timestamptz,
  primary key (user_id, kind, record_key)
);
create index if not exists vueo_records_changes_idx on vueo_private.records(user_id,change_id);
alter table vueo_private.account_keys enable row level security;
alter table vueo_private.records enable row level security;
revoke all on all tables in schema vueo_private from public, anon, authenticated;
revoke all on all sequences in schema vueo_private from public, anon, authenticated;
grant usage on schema public to authenticated;

create or replace function vueo_private.record_json(r vueo_private.records)
returns jsonb language sql security definer set search_path = '' as $$
  select jsonb_build_object('kind',(r).kind,'key',(r).record_key,'deleted',(r).deleted,
    'revision',(r).revision,'value',case when (r).deleted or (r).encrypted_value is null then null
      else extensions.pgp_sym_decrypt((r).encrypted_value,k.secret)::jsonb end)
  from vueo_private.account_keys k where k.user_id = (r).user_id;
$$;
revoke all on function vueo_private.record_json(vueo_private.records) from public, anon, authenticated;

create or replace function public.vueo_sync_exchange(
  p_changes jsonb default '[]', p_kinds text[] default '{}',
  p_after text default '0', p_limit integer default 250
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  uid uuid := auth.uid(); secret_key text; c jsonb; r vueo_private.records;
  acknowledgements jsonb := '[]'; rows_json jsonb := '[]'; cursor_key text := p_after;
  accepted boolean; n integer := 0;
begin
  if uid is null then raise exception 'Sign in required'; end if;
  if jsonb_typeof(p_changes) <> 'array' or jsonb_array_length(p_changes) > 50
    or octet_length(p_changes::text) > 2097152 then raise exception 'Invalid batch'; end if;
  if p_limit < 1 or p_limit > 250 then raise exception 'Invalid page size'; end if;
  -- Serializes record CAS and token leases for this account, only during this transaction.
  perform pg_advisory_xact_lock(hashtextextended(uid::text, 0));
  insert into vueo_private.account_keys(user_id) values(uid) on conflict do nothing;
  select secret into secret_key from vueo_private.account_keys where user_id = uid;
  for c in select value from jsonb_array_elements(p_changes) loop
    if not coalesce((c->>'deleted')::boolean,false) and jsonb_typeof(c->'value') is distinct from 'object' then
      raise exception 'Invalid payload';
    end if;
    if c->>'kind' not in ('profiles','favorites','progress','history','addons','catalogs','integrations','trakt')
      or c->>'key' is null or length(c->>'key') not between 1 and 2048
      or octet_length(coalesce(c->'value','null'::jsonb)::text) > 524288
      or coalesce((c->>'revision')::bigint,-1) < 0 then raise exception 'Invalid record'; end if;
    if c->>'kind' = 'trakt' and c->>'key' like 'session:%' then
      raise exception 'Token sessions require token lease';
    end if;
    select * into r from vueo_private.records
      where user_id = uid and kind = c->>'kind' and record_key = c->>'key' for update;
    accepted := false;
    if not found then
      if (c->>'revision')::bigint = 0 then
        insert into vueo_private.records(user_id,kind,record_key,encrypted_value,deleted,revision)
        values(uid,c->>'kind',c->>'key',case when coalesce((c->>'deleted')::boolean,false) then null
          else extensions.pgp_sym_encrypt((c->'value')::text,secret_key,'cipher-algo=aes256,compress-algo=1') end,
          coalesce((c->>'deleted')::boolean,false),1) returning * into r;
        accepted := true;
      else raise exception 'Unknown revision'; end if;
    elsif r.revision = (c->>'revision')::bigint then
      update vueo_private.records set encrypted_value = case when coalesce((c->>'deleted')::boolean,false) then null
        else extensions.pgp_sym_encrypt((c->'value')::text,secret_key,'cipher-algo=aes256,compress-algo=1') end,
        deleted = coalesce((c->>'deleted')::boolean,false), revision = revision+1, change_id=nextval('vueo_private.changes'), updated_at = clock_timestamp()
      where user_id=uid and kind=r.kind and record_key=r.record_key returning * into r;
      accepted := true;
    end if;
    acknowledgements := acknowledgements || jsonb_build_array(
      vueo_private.record_json(r) || jsonb_build_object('accepted',accepted));
  end loop;
  for r in select * from vueo_private.records
    where user_id=uid and kind=any(p_kinds)
      and not (kind='trakt' and record_key like 'session:%')
      and change_id > p_after::bigint
    order by change_id limit p_limit loop
    rows_json := rows_json || jsonb_build_array(vueo_private.record_json(r));
    cursor_key := r.change_id::text;
    n := n+1;
  end loop;
  return jsonb_build_object('acks',acknowledgements,'rows',rows_json,'cursor',cursor_key,
    'next',case when n = p_limit then cursor_key else '' end);
end;
$$;
revoke all on function public.vueo_sync_exchange(jsonb,text[],text,integer) from public, anon;
grant execute on function public.vueo_sync_exchange(jsonb,text[],text,integer) to authenticated;

create or replace function public.vueo_trakt_claim(p_profile text,p_lease uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare uid uuid := auth.uid(); r vueo_private.records;
begin
  if uid is null then raise exception 'Sign in required'; end if;
  if length(p_profile) not between 1 and 100 or p_lease is null then raise exception 'Invalid lease'; end if;
  perform pg_advisory_xact_lock(hashtextextended(uid::text,0));
  insert into vueo_private.account_keys(user_id) values(uid) on conflict do nothing;
  insert into vueo_private.records(user_id,kind,record_key,deleted,revision)
    values(uid,'trakt','session:'||p_profile,true,0) on conflict do nothing;
  select * into r from vueo_private.records where user_id=uid and kind='trakt'
    and record_key='session:'||p_profile for update;
  if r.lease_until > clock_timestamp() and r.lease_id is distinct from p_lease then
    return jsonb_build_object('acquired',false);
  end if;
  update vueo_private.records set lease_id=p_lease,lease_until=clock_timestamp()+interval '90 seconds'
    where user_id=uid and kind='trakt' and record_key=r.record_key returning * into r;
  return vueo_private.record_json(r) || jsonb_build_object('acquired',true);
end;
$$;
revoke all on function public.vueo_trakt_claim(text,uuid) from public, anon;
grant execute on function public.vueo_trakt_claim(text,uuid) to authenticated;

create or replace function public.vueo_trakt_release(
  p_profile text,p_lease uuid,p_session jsonb default null,p_delete boolean default false
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare uid uuid := auth.uid(); r vueo_private.records; secret_key text;
begin
  if uid is null then raise exception 'Sign in required'; end if;
  perform pg_advisory_xact_lock(hashtextextended(uid::text,0));
  select * into r from vueo_private.records where user_id=uid and kind='trakt'
    and record_key='session:'||p_profile for update;
  if not found or r.lease_id is distinct from p_lease or r.lease_until <= clock_timestamp() then
    raise exception 'Token lease expired';
  end if;
  if p_session is not null and not p_delete then
    if jsonb_typeof(p_session)<>'object' or octet_length(p_session::text)>16384
      or coalesce(p_session->>'access_token','')='' or coalesce(p_session->>'refresh_token','')='' then
      raise exception 'Invalid token session'; end if;
    select secret into secret_key from vueo_private.account_keys where user_id=uid;
    update vueo_private.records set encrypted_value=extensions.pgp_sym_encrypt(p_session::text,secret_key,'cipher-algo=aes256'),
      deleted=false,revision=revision+1,change_id=nextval('vueo_private.changes'),updated_at=clock_timestamp()
      where user_id=uid and kind='trakt' and record_key=r.record_key;
  elsif p_delete then
    update vueo_private.records set encrypted_value=null,deleted=true,revision=revision+1,change_id=nextval('vueo_private.changes'),updated_at=clock_timestamp()
      where user_id=uid and kind='trakt' and record_key=r.record_key;
  end if;
  update vueo_private.records set lease_id=null,lease_until=null
    where user_id=uid and kind='trakt' and record_key=r.record_key returning * into r;
  return vueo_private.record_json(r);
end;
$$;
revoke all on function public.vueo_trakt_release(text,uuid,jsonb,boolean) from public, anon;
grant execute on function public.vueo_trakt_release(text,uuid,jsonb,boolean) to authenticated;
notify pgrst,'reload schema';
commit;
select 'VUEO sync v2 setup complete' as result;
