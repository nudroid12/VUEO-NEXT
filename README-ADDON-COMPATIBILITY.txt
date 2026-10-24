VUEO Addon Compatibility - Mobile + TV

Pasang:
1. Ekstrak ZIP.
2. Upload folder mobile/, tv/ dan shared/ ke root repo VUEO. Gantikan fail yang sama.
3. Sertakan docs/, LICENSES/ dan validation/ untuk notis dan rujukan semakan.
4. Build dan pasang APK Mobile/TV baharu.
5. Cuba tambah addon HTTP sebenar anda. URL asas atau /manifest.json diterima.

Tiada fail perlu dipadam. Tiada YML atau perubahan dependency Gradle.

Pembaikan: HTTP/HTTPS, Stremio URL, URL query konfigurasi, resource types/idPrefixes,
subtitle dalam stream, header subtitle, MIME dan pemeliharaan behavior hints terpilih.
Perubahan ini mengadaptasi bahagian Nuvio terpilih, bukan mengganti seluruh sistem addon.

Dasar discovery subtitle, timeout/retry, UI pengurusan, cache dan plugin kekal.
Tidak mengaktifkan handler baharu bagi externalUrl-only, ytId-only atau clientResolve-only.
Metadata hints tambahan disimpan; tidak mengaktifkan autoplay/debrid baharu.

Patch dibina di atas snapshot repo (56) dan patch terdahulu dalam sesi ini.
Ia tidak memasukkan semula ProviderHlsDataSource.kt daripada patch data URI sebelum ini;
pastikan patch data URI itu juga sudah berada dalam repo jika diperlukan.

60 semakan lulus: 30 integrasi JVM, 3 header Media3 dan 27 ujian regresi.
Build APK penuh dan ujian peranti belum dibuat.
