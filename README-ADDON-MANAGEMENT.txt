VUEO Addon Management UI - Mobile + TV

Pasang selepas patch Addon HTTP/Manifest/Stream/Subtitles sebelum ini.
Upload mobile/, tv/ dan shared/ ke root repo dan gantikan fail sama.
Build dan pasang APK baharu. Tiada YML. Tiada fail perlu dipadam.

Mobile: status refresh pada kad addon, mesej ralat ringkas, dan tindakan kad
dikunci semasa kad itu direfresh. URL pemasangan menerangkan HTTP/HTTPS/Stremio.
TV: tekan kanan pada addon untuk Manage > Configure, Refresh atau Remove.
Configure muncul jika addon menyokong konfigurasi. Dialog pemasangan kekal
terbuka semasa memuatkan dan jika gagal, dengan URL masih di medan input.
Refresh satu addon mengekalkan pilihan enabled/disabled dan tidak memasang
semula addon yang dibuang semasa request berjalan.

Tiada perubahan susunan addon, discovery subtitle, cache policy atau playback.

Semakan: helper mesej ralat dikompil dan 7 kategori kegagalan diperiksa;
wiring status, dialog busy dan pemeliharaan enabled state disemak pada kod.
Build APK penuh dan ujian visual/fokus pada Mobile/TV belum dibuat.
