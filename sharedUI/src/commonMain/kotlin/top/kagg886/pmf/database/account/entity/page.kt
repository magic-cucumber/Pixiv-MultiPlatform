package top.kagg886.pmf.database.account.entity

import androidx.room3.Entity

/**
 * ================================================
 * Author:     iveou
 * Created on: 2026/8/10 14:02
 * ================================================
 */

@Entity(
    tableName = "page_key",
    primaryKeys = ["tag", "page"],
)
data class PageKey(
    val tag: String,
    /** Number of network batches that have been committed for [tag]. */
    val page: Int,
    /** Serialized request for the next append, or null when pagination has ended. */
    val nextPayload: String?,
)
