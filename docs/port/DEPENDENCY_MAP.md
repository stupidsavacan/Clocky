# AOSP Soong → Gradle dependency map

Mappings come from the pinned DeskClock `Android.bp` plus imports found in the copied source tree.

| AOSP static_lib | Import prefix | Gradle coordinate | Imported symbols |
|---|---|---|---:|
| `androidx.annotation_annotation` | `androidx.annotation` | `androidx.annotation:annotation:1.9.1` | 40 |
| `androidx.collection_collection` | `androidx.collection` | `androidx.collection:collection:1.4.5` | 1 |
| `androidx.arch.core_core-common` | `androidx.arch.core` | `androidx.arch.core:core-common:2.2.0` | 0 |
| `androidx.lifecycle_lifecycle-common` | `androidx.lifecycle` | `androidx.lifecycle:lifecycle-common:2.8.7` | 0 |
| `androidx.lifecycle_lifecycle-runtime` | `androidx.lifecycle` | `androidx.lifecycle:lifecycle-runtime-ktx:2.8.7` | 0 |
| `com.google.android.material_material` | `com.google.android.material` | `com.google.android.material:material:1.12.0` | 7 |
| `androidx.percentlayout_percentlayout` | `androidx.percentlayout` | `androidx.percentlayout:percentlayout:1.0.0` | 0 |
| `androidx.transition_transition` | `androidx.transition` | `androidx.transition:transition:1.5.1` | 0 |
| `androidx.core_core` | `androidx.core` | `androidx.core:core-ktx:1.15.0` | 33 |
| `androidx.legacy_legacy-support-core-ui` | `androidx.legacy` | `androidx.legacy:legacy-support-core-ui:1.0.0` | 0 |
| `androidx.media_media` | `androidx.media` | `androidx.media:media:1.7.0` | 0 |
| `androidx.legacy_legacy-support-v13` | `androidx.legacy.app` | `androidx.legacy:legacy-support-v13:1.0.0` | 0 |
| `androidx.preference_preference` | `androidx.preference` | `androidx.preference:preference-ktx:1.2.1` | 12 |
| `androidx.appcompat_appcompat` | `androidx.appcompat` | `androidx.appcompat:appcompat:1.7.0` | 13 |
| `androidx.gridlayout_gridlayout` | `androidx.gridlayout` | `androidx.gridlayout:gridlayout:1.0.0` | 0 |
| `androidx.recyclerview_recyclerview` | `androidx.recyclerview` | `androidx.recyclerview:recyclerview:1.3.2` | 23 |

## Imported external symbols

| Import | Count |
|---|---:|
| `androidx.annotation.AnyRes` | 1 |
| `androidx.annotation.AttrRes` | 1 |
| `androidx.annotation.ColorInt` | 4 |
| `androidx.annotation.DrawableRes` | 4 |
| `androidx.annotation.IdRes` | 1 |
| `androidx.annotation.IntDef` | 1 |
| `androidx.annotation.Keep` | 3 |
| `androidx.annotation.StringRes` | 16 |
| `androidx.annotation.VisibleForTesting` | 9 |
| `androidx.appcompat.app.ActionBar` | 1 |
| `androidx.appcompat.app.AlertDialog` | 3 |
| `androidx.appcompat.app.AppCompatActivity` | 2 |
| `androidx.appcompat.widget.AppCompatEditText` | 1 |
| `androidx.appcompat.widget.AppCompatImageView` | 1 |
| `androidx.appcompat.widget.AppCompatTextView` | 1 |
| `androidx.appcompat.widget.SearchView` | 2 |
| `androidx.appcompat.widget.SearchView.OnQueryTextListener` | 1 |
| `androidx.appcompat.widget.Toolbar` | 1 |
| `androidx.collection.ArrayMap` | 1 |
| `androidx.coordinatorlayout.widget.CoordinatorLayout` | 1 |
| `androidx.core.app.NotificationCompat` | 3 |
| `androidx.core.app.NotificationCompat.Action` | 2 |
| `androidx.core.app.NotificationCompat.Builder` | 2 |
| `androidx.core.app.NotificationManagerCompat` | 8 |
| `androidx.core.app.NotificationManagerCompat.IMPORTANCE_HIGH` | 1 |
| `androidx.core.app.NotificationManagerCompat.IMPORTANCE_LOW` | 1 |
| `androidx.core.content.ContextCompat` | 6 |
| `androidx.core.graphics.ColorUtils` | 2 |
| `androidx.core.graphics.drawable.DrawableCompat` | 1 |
| `androidx.core.os.BuildCompat` | 1 |
| `androidx.core.view.AccessibilityDelegateCompat` | 1 |
| `androidx.core.view.ViewCompat` | 2 |
| `androidx.core.view.accessibility.AccessibilityNodeInfoCompat` | 1 |
| `androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat` | 1 |
| `androidx.core.view.animation.PathInterpolatorCompat` | 1 |
| `androidx.fragment.app.DialogFragment` | 3 |
| `androidx.fragment.app.Fragment` | 7 |
| `androidx.fragment.app.FragmentManager` | 5 |
| `androidx.fragment.app.FragmentTransaction` | 2 |
| `androidx.interpolator.view.animation.FastOutSlowInInterpolator` | 1 |
| `androidx.loader.app.LoaderManager` | 1 |
| `androidx.loader.app.LoaderManager.LoaderCallbacks` | 2 |
| `androidx.loader.content.AsyncTaskLoader` | 1 |
| `androidx.loader.content.CursorLoader` | 1 |
| `androidx.loader.content.Loader` | 2 |
| `androidx.preference.DropDownPreference` | 1 |
| `androidx.preference.ListPreference` | 2 |
| `androidx.preference.ListPreferenceDialogFragmentCompat` | 1 |
| `androidx.preference.Preference` | 3 |
| `androidx.preference.PreferenceDialogFragmentCompat` | 1 |
| `androidx.preference.PreferenceFragmentCompat` | 2 |
| `androidx.preference.PreferenceViewHolder` | 1 |
| `androidx.preference.TwoStatePreference` | 1 |
| `androidx.recyclerview.widget.LinearLayoutManager` | 4 |
| `androidx.recyclerview.widget.RecyclerView` | 8 |
| `androidx.recyclerview.widget.RecyclerView.ItemAnimator` | 1 |
| `androidx.recyclerview.widget.RecyclerView.NO_ID` | 4 |
| `androidx.recyclerview.widget.RecyclerView.State` | 1 |
| `androidx.recyclerview.widget.RecyclerView.ViewHolder` | 3 |
| `androidx.recyclerview.widget.SimpleItemAnimator` | 2 |
| `androidx.vectordrawable.graphics.drawable.VectorDrawableCompat` | 1 |
| `androidx.viewpager.widget.PagerAdapter` | 2 |
| `androidx.viewpager.widget.ViewPager` | 3 |
| `androidx.viewpager.widget.ViewPager.OnPageChangeListener` | 1 |
| `androidx.viewpager.widget.ViewPager.SCROLL_STATE_DRAGGING` | 1 |
| `androidx.viewpager.widget.ViewPager.SCROLL_STATE_IDLE` | 1 |
| `androidx.viewpager.widget.ViewPager.SCROLL_STATE_SETTLING` | 1 |
| `com.google.android.material.snackbar.Snackbar` | 6 |
| `com.google.android.material.tabs.TabLayout` | 1 |
