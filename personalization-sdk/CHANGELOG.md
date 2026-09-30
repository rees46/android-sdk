# personalization-sdk changelog

## Unreleased

### Features

* In-app popups find the activity on screen themselves, so `initializeFragmentManager` is no longer needed. `SDK.popupPresentationListener` (`PopupPresentationListener.shouldPresentPopup(sdk, popup)`) lets the host pick the activity for a popup, or return `null` to hold it back or draw it itself — a popup drawn by the host is reported with `sdk.tracking.popupShown(popup.id)`. `SDK.enableAutoPopupPresentation` / `Rees46Config.enableAutoPopupPresentation` turns automatic presentation off.
* Search responses carry more of what the server sends: `Product.oldPrice`, `oldPriceFormatted`, `discount`, `discountFormatted`, `isNew`; `SearchBlankResponse.lastQueries`; `SearchFullResponse.filters` and `industrialFilters`; instant search `queries` as typed `Query` items.
* Purchase tracking: optional `isGiftPackage` on `PurchaseTrackingRequest` — sent as `gift_package` only when `true`, the way `tax_free` is.
* Strict purchase tracking: `SDK.trackPurchase(PurchaseTrackingRequest, …)` with `PurchaseItemRequest` / `PurchaseTrackingRequest` (camelCase in public API; wire keys snake_case inside serialization). Client validation before network; `tax_free` only when `isTaxFree` is true; optional fields omitted when unset. Demo app and Espresso e2e taps for minimal and full payloads.

### Behaviour changes

* Popups are shown automatically in the `FragmentActivity` on screen, including in apps that never called `initializeFragmentManager` — there they used not to appear at all. Set `enableAutoPopupPresentation = false` to keep them off.
* `SearchInstantResponse.queries` is `List<Query>` instead of `List<Any>`; code that cast its items to `Map` should read the `Query` fields instead.

### Fixes

* A popup that cannot be shown no longer fails the init or tracking request that brought it; popups are shown on the main thread, and a throwing presentation listener costs the popup, not the app.

### Deprecations

* `SDK.initializeFragmentManager` / `InAppNotificationManager.initFragmentManager` — popups follow the activity on screen without it.
* `TrackEvent.PURCHASE` (and `track(TrackEvent.PURCHASE, Params, ...)`) — use `SDK.trackPurchase(PurchaseTrackingRequest, ...)` instead.

## Earlier releases

See the repository root and release tags for history prior to this file.
