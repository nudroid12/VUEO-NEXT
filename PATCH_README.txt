Replace included files from repository root. Based on repo (34) with previous session changes.
TV Home return: reuse decoded cached images before exact layout size is known; retain saveable Home list positions per profile; avoid resetting vertical scroll on re-entry; hide loading when presentation rows already exist.
Catalog refresh and Continue Watching stay enabled. Image-cache budget unchanged. Images absent from memory still require disk/network loading.
Source delimiter checks and ZIP integrity passed. No build or device test performed.
Test Home -> detail/Search/Library -> Back, scrolled rows, profile switching, cache eviction and catalog refresh.
