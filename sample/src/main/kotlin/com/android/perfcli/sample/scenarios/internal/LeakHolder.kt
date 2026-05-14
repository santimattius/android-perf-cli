package com.android.perfcli.sample.scenarios.internal

import android.app.Activity
import java.lang.ref.WeakReference

/**
 * Singleton used by MemoryLeakActivity to demonstrate static Activity leaks.
 *
 * Bad path: [activityRef] holds a strong reference — prevents GC after Activity destruction.
 * Fixed path: [weakRef] holds a WeakReference — allows GC after Activity is destroyed.
 */
object LeakHolder {
    var activityRef: Activity? = null
    var weakRef: WeakReference<Activity>? = null
}
