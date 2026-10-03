VUEO TV Details hero + episode cards

Apply these four files to the current cumulative project, preserving their paths.
Includes the earlier lower-left hero and viewport/focus corrections; no need to apply those separate Details ZIPs first.

Reference inspected: NuvioMedia/NuvioTV tag 0.8.3-beta, HeroSection.kt, EpisodesSection.kt, MetaDetailsScreen.kt.
https://github.com/NuvioMedia/NuvioTV/tree/0.8.3-beta

Hero uses exact viewport height and bottom alignment. Focus scrolls only enough to reveal an offscreen control. Seasons/episodes sit below the initial hero.
Episode cards adapt to viewport width, overlay episode number/title/synopsis/date, retain playback progress and remote navigation.
Runtime uses playback duration when available, otherwise the existing media runtime (which may be a series average). Missing synopsis/date/runtime are omitted. No invented episode rating; the existing hero rating row is unchanged.
Backdrop stays visible, darkened, when browsing lower sections. No trailer added.

Validation: source review, hero rating helper equality against prior source, ZIP integrity. No local build or device runtime test performed. Check initial Play focus, Down to season/episodes, Left/Right episode navigation, Up to hero, and return from playback on your TV.
