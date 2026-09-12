package com.alexk.tvlock

import android.app.Activity
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast

class BlockedAppsActivity : Activity() {

    private data class Row(val pkg: String, val label: String, var blocked: Boolean)

    private lateinit var rows: List<Row>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked)
        val list = findViewById<ListView>(R.id.apps_list)

        // merge LAUNCHER + LEANBACK_LAUNCHER (TV apps register only in leanback)
        val pm = packageManager
        val blockedSet = Prefs(this).blockedList()
        val seen = mutableSetOf<String>()
        val launchers = mutableListOf<ResolveInfo>()
        for (cat in listOf(Intent.CATEGORY_LAUNCHER, "android.intent.category.LEANBACK_LAUNCHER")) {
            launchers += pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(cat), 0)
        }
        rows = launchers
            .map { ri: ResolveInfo -> ri.activityInfo.packageName }
            .filter { it != packageName && seen.add(it) }
            .map { pkg ->
                val label = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (_: Exception) { pkg }
                Row(pkg, label, pkg in blockedSet)
            }
            .sortedWith(compareByDescending<Row> { it.blocked }.thenBy { it.label.lowercase() })

        fun render() {
            list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
                rows.map { (if (it.blocked) "🔒 " else "    ") + it.label })
        }
        render()

        list.setOnItemClickListener { _, _, position, _ ->
            val row = rows[position]
            if (row.pkg == packageName) {
                Toast.makeText(this, R.string.self_block_guard, Toast.LENGTH_SHORT).show()
                return@setOnItemClickListener
            }
            row.blocked = !row.blocked
            val set = Prefs(this).blockedList().toMutableSet()
            set.remove(packageName) // never let the gate block itself
            if (row.blocked) set.add(row.pkg) else set.remove(row.pkg)
            Prefs(this).setBlocked(set)
            LockPollService.instance?.reloadPrefs()
            render()
            Toast.makeText(this, if (row.blocked) "🔒 ${row.label}" else "✓ ${row.label}",
                Toast.LENGTH_SHORT).show()
        }
    }
}
