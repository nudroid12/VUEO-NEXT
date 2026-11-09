# Mobile addon manifest URL display

Replace MobileAddons.kt at the provided repository path, then rebuild Mobile.

The card now displays descriptor.baseUrl, the normalized manifest URL stored by StremioAddonExtension.fromManifestUrl(), and Copy uses the same value. The label is Manifest URL. This also displays the manifest URL for addons without an Address suffix in their description. Configured paths and query strings are retained as stored.

One Mobile UI file, two edits only. Description cleanup is unchanged. No installation, configuration, network, provider, TV, or subtitle changes.

Source checks passed for display/Copy binding and edit scope. Android build and device UI test not run in this environment.
