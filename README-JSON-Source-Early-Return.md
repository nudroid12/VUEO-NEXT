# VUEO — tamat awal selepas respons sumber layak

## Pasang melalui GitHub browser

1. Ekstrak ZIP ini.
2. Upload kandungan folder `shared` ke root repo VUEO, mengekalkan laluan folder. Fail baharu `JsonSourceCompletionPolicy.kt` mesti disertakan bersama `PluginWebViewResolver.kt`.
3. Commit, tunggu build TV/Mobile biasa, kemudian pasang APK baharu pada peranti yang digunakan.
4. Pasang patch Cloudbank VidLove v2.4.0 dan refresh repo/provider.

Fail validation dan nota ini pilihan untuk disimpan. Kedua-dua fail Kotlin diperlukan untuk build. Tiada fail YML atau dependency baharu.

## Perubahan

Provider boleh menghantar pilihan generik `jsonSourceCompletion`. Selepas JSON ditangkap, bridge menyemak URL request dan query yang diwajibkan, identiti metadata, host HTTPS media, format serta pilihan kualiti/audio. Apabila pilihan yang akan digunakan provider layak, sesi dijadualkan tamat melalui finishAfterFirstMs (150 ms untuk VidLove) dengan `finish=json-source`.

Ini ialah pengesahan calon sumber untuk discovery, bukan bukti media telah dimainkan. `mediaReady` tidak ditandakan benar atas dasar JSON. Provider tetap menyemak respons sekali lagi.

Pilihan ini tidak aktif bagi provider yang tidak menghantarnya. Request HTTP VidLove masih dicuba dahulu. Tiada fetch media, manifest, segmen atau subtitle tambahan. Pemetaan signed URL, quality, audio, playback headers dan subtitle tidak berubah.

Bridge menggunakan baseline terkini APP-VUEO-Player-Stage-Diagnostics-v2-TV-Mobile. Perubahan frame-local playback dan stage diagnostics dikekalkan; aset interaksi frame versi sama disertakan untuk memastikan dependency sedia ada tersedia.

## Perlindungan

- Respons kosong, ID atau episod salah tidak menyebabkan tamat awal.
- Pilihan diketahui bawah 720p dan dub-only yang dinyatakan ditolak; Auto/unknown dibenarkan seperti provider.
- Pemilihan rendition H.264/kualiti diselaraskan dengan provider sebelum semakan audio, supaya rendition lain yang layak tidak menyebabkan tamat awal untuk rendition terpilih yang tidak layak.
- Badan JSON kekal dihadkan 256 KiB. Hook opt-in menerima sehingga 8 respons; buffer native kekal 4 dan menyimpan respons layak jika dipenuhi respons kosong terdahulu.
- Tiada ID tajuk atau domain VidLove dimasukkan dalam policy app.

## Pengesahan

Fail bridge lengkap dan helper baharu berjaya dikompilasi dengan Android API 35/AndroidX WebKit. 28 ujian policy Kotlin, 18 ujian fallback provider, 12 ujian pemetaan serta suite regresi URL shim dan frame interaction lulus. Replay payload sebenar tiga ID mengesahkan medan playback sebelum/selepas tidak berubah. Validasi Cloudbank 36 provider lulus.

APK penuh belum dibina di sini. Penjimatan masa sebenar dan playback selepas tamat awal masih memerlukan ujian telefon. Tiada janji masa scan tertentu; HTTP, masa respons website dan overhead native masih wujud.

## Log yang dijangka

VidLove v2.4.0 sepatutnya menunjukkan `WEBSITE_STATE: finish=json-source ... earlyCompletion=true jsonEligible=true` bagi payload layak. Jika `earlyCompletion=false`, app masih menggunakan bridge lama. Jika tamat melalui timeout, respons mungkin belum layak atau belum ditangkap.
