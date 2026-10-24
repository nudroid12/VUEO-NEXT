VUEO - Addon Playback Startup + Mobile Address Copy

Pasang selepas patch Addon HTTP/Manifest dan Addon Management UI sebelum ini.
Upload mobile/, tv/ dan shared/ ke root repo, gantikan fail sama.
Build dan pasang APK Mobile/TV baharu. Tiada YML atau fail perlu dipadam.

Playback Mobile + TV:
Sumber addon tidak lagi dipotong oleh timer frame pertama 15 saat atau timer
sumber auto-recovery 5 saat. Playback HTTP biasa menggunakan connect/read timeout
15 saat dan pengendalian error/retry player sedia ada. Ini adaptasi tingkah laku
startup Nuvio, bukan penggantian seluruh player atau jaminan semua link boleh main.
Tempoh keseluruhan loading boleh melebihi 15 saat. Back boleh digunakan untuk keluar.

Provider plugin (ID plugin:...) dan sumber tanpa identiti mengekalkan had startup
sedia ada. Dasar selepas frame pertama, termasuk had rebuffer 25 saat, dikekalkan.
Transport khusus shared yang diminta provider kekal melalui laluan sedia ada.

Mobile Addons:
Address: HTTP/HTTPS yang berada di hujung description diasingkan daripada teks.
Alamat dipaparkan penuh dengan pembalutan baris dan butang Copy URL; alamat penuh
disalin dengan mesej URL copied. Ini alamat yang diberi addon, bukan manifest URL
konfigurasi yang mungkin mengandungi credentials. Addon tanpa Address kekal biasa.
Tiada perubahan VidLove, subtitle discovery atau susunan addon.

Semakan:
10 semakan helper Kotlin lulus (pemisahan URL dan skop polisi addon/plugin).
Pembinaan datasource dengan API Media3 1.11.0 berjaya dikompil secara berasingan.
Sambungan polisi kepada watchdog Mobile/TV disemak pada kod.
Build APK penuh, main balik sebenar dan ujian visual/clipboard peranti belum dibuat.
