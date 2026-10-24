VUEO - Data URI Playback Fix (Mobile + TV)

Masalah:
Log menunjukkan MalformedURLException: unknown protocol: data. Laluan data:
dihantar ke pemuat HTTP walaupun kandungannya disimpan terus dalam URI.

Pembaikan:
ProviderHlsDataSource kini menggunakan Media3 DataSchemeDataSource untuk URI data:.
HTTP/HTTPS, header, pengangkutan HTTP pilihan pengguna dan pemprosesan HLS
provider kekal melalui laluan sedia ada. Mobile dan TV berkongsi fail ini.

Cara pasang:
1. Ekstrak ZIP.
2. Upload folder shared/ ke root repo VUEO dan gantikan fail yang sama.
3. Build dan pasang APK Mobile/TV baharu, kemudian uji cerita yang gagal tadi.
Tiada fail perlu dipadam. Tiada YML. Tiada perubahan provider atau UI.

Semakan:
12 semakan JVM lulus dengan kelas sebenar Media3 1.11.0 dan fixture Android
untuk Uri, Base64 dan Util. Meliputi data Base64, teks, position/length,
EOF, data rosak, uppercase scheme, transfer listener, HTTPS/header,
penggunaan semula sumber dan pemprosesan HLS plain.
Build APK penuh dan ujian peranti belum dibuat. Semakan ini tidak menguji
main balik keseluruhan HLS atau menjamin masalah buffering rangkaian selesai.
