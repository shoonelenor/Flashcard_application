package com.example.stardeckapplication.db

import android.content.ContentValues

class FriendDao(private val dbHelper: StarDeckDbHelper) {

    private val readable get() = dbHelper.readableDatabase
    private val writable get() = dbHelper.writableDatabase

    data class UserLite(
        val id: Long,
        val name: String,
        val email: String
    )

    data class FriendRow(
        val friendshipId: Long,
        val otherUserId: Long,
        val otherUserName: String,
        val otherUserEmail: String,
        val status: String,
        val createdAt: Long
    )

    fun searchUsersForFriend(currentUserId: Long, query: String): List<UserLite> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()

        val out = mutableListOf<UserLite>()
        readable.rawQuery(
            """
            SELECT u.${DbContract.U_ID}, u.${DbContract.U_NAME}, u.${DbContract.U_EMAIL}
            FROM ${DbContract.T_USERS} u
            WHERE u.${DbContract.U_ID} <> ?
              AND (
                    LOWER(u.${DbContract.U_NAME}) LIKE '%' || LOWER(?) || '%'
                    OR LOWER(u.${DbContract.U_EMAIL}) LIKE '%' || LOWER(?) || '%'
                  )
              AND NOT EXISTS (
                    SELECT 1
                    FROM ${DbContract.T_FRIENDSHIPS} f
                    WHERE (
                            (f.${DbContract.F_REQUESTER_USER_ID} = ? AND f.${DbContract.F_ADDRESSEE_USER_ID} = u.${DbContract.U_ID})
                            OR
                            (f.${DbContract.F_ADDRESSEE_USER_ID} = ? AND f.${DbContract.F_REQUESTER_USER_ID} = u.${DbContract.U_ID})
                          )
                      AND f.${DbContract.F_STATUS} IN (?, ?)
                  )
            ORDER BY u.${DbContract.U_NAME} ASC, u.${DbContract.U_EMAIL} ASC
            LIMIT 30
            """.trimIndent(),
            arrayOf(
                currentUserId.toString(),
                q,
                q,
                currentUserId.toString(),
                currentUserId.toString(),
                DbContract.FRIEND_PENDING,
                DbContract.FRIEND_ACCEPTED
            )
        ).use { c ->
            while (c.moveToNext()) {
                out += UserLite(
                    id = c.getLong(0),
                    name = c.getString(1),
                    email = c.getString(2)
                )
            }
        }
        return out
    }

    fun sendFriendRequest(fromUserId: Long, toUserId: Long): Long {
        if (fromUserId == toUserId) return -1L
        if (hasActiveRelationship(fromUserId, toUserId)) return -1L

        val cv = ContentValues().apply {
            put(DbContract.F_REQUESTER_USER_ID, fromUserId)
            put(DbContract.F_ADDRESSEE_USER_ID, toUserId)
            put(DbContract.F_STATUS, DbContract.FRIEND_PENDING)
            put(DbContract.F_CREATED_AT, System.currentTimeMillis())
            putNull(DbContract.F_RESPONDED_AT)
        }
        return writable.insert(DbContract.T_FRIENDSHIPS, null, cv)
    }

    fun getIncomingRequests(userId: Long): List<FriendRow> {
        val out = mutableListOf<FriendRow>()
        readable.rawQuery(
            """
            SELECT
                f.${DbContract.F_ID},
                u.${DbContract.U_ID},
                u.${DbContract.U_NAME},
                u.${DbContract.U_EMAIL},
                f.${DbContract.F_STATUS},
                f.${DbContract.F_CREATED_AT}
            FROM ${DbContract.T_FRIENDSHIPS} f
            INNER JOIN ${DbContract.T_USERS} u
                ON u.${DbContract.U_ID} = f.${DbContract.F_REQUESTER_USER_ID}
            WHERE f.${DbContract.F_ADDRESSEE_USER_ID} = ?
              AND f.${DbContract.F_STATUS} = ?
            ORDER BY f.${DbContract.F_CREATED_AT} DESC
            """.trimIndent(),
            arrayOf(userId.toString(), DbContract.FRIEND_PENDING)
        ).use { c ->
            while (c.moveToNext()) {
                out += FriendRow(
                    friendshipId = c.getLong(0),
                    otherUserId = c.getLong(1),
                    otherUserName = c.getString(2),
                    otherUserEmail = c.getString(3),
                    status = c.getString(4),
                    createdAt = c.getLong(5)
                )
            }
        }
        return out
    }

    fun getSentRequests(userId: Long): List<FriendRow> {
        val out = mutableListOf<FriendRow>()
        readable.rawQuery(
            """
            SELECT
                f.${DbContract.F_ID},
                u.${DbContract.U_ID},
                u.${DbContract.U_NAME},
                u.${DbContract.U_EMAIL},
                f.${DbContract.F_STATUS},
                f.${DbContract.F_CREATED_AT}
            FROM ${DbContract.T_FRIENDSHIPS} f
            INNER JOIN ${DbContract.T_USERS} u
                ON u.${DbContract.U_ID} = f.${DbContract.F_ADDRESSEE_USER_ID}
            WHERE f.${DbContract.F_REQUESTER_USER_ID} = ?
              AND f.${DbContract.F_STATUS} = ?
            ORDER BY f.${DbContract.F_CREATED_AT} DESC
            """.trimIndent(),
            arrayOf(userId.toString(), DbContract.FRIEND_PENDING)
        ).use { c ->
            while (c.moveToNext()) {
                out += FriendRow(
                    friendshipId = c.getLong(0),
                    otherUserId = c.getLong(1),
                    otherUserName = c.getString(2),
                    otherUserEmail = c.getString(3),
                    status = c.getString(4),
                    createdAt = c.getLong(5)
                )
            }
        }
        return out
    }

    fun getFriends(userId: Long): List<FriendRow> {
        val out = mutableListOf<FriendRow>()
        readable.rawQuery(
            """
            SELECT
                f.${DbContract.F_ID},
                u.${DbContract.U_ID},
                u.${DbContract.U_NAME},
                u.${DbContract.U_EMAIL},
                f.${DbContract.F_STATUS},
                f.${DbContract.F_CREATED_AT}
            FROM ${DbContract.T_FRIENDSHIPS} f
            INNER JOIN ${DbContract.T_USERS} u
                ON u.${DbContract.U_ID} = CASE
                    WHEN f.${DbContract.F_REQUESTER_USER_ID} = ? THEN f.${DbContract.F_ADDRESSEE_USER_ID}
                    ELSE f.${DbContract.F_REQUESTER_USER_ID}
                END
            WHERE (f.${DbContract.F_REQUESTER_USER_ID} = ? OR f.${DbContract.F_ADDRESSEE_USER_ID} = ?)
              AND f.${DbContract.F_STATUS} = ?
            ORDER BY u.${DbContract.U_NAME} ASC, u.${DbContract.U_EMAIL} ASC
            """.trimIndent(),
            arrayOf(
                userId.toString(),
                userId.toString(),
                userId.toString(),
                DbContract.FRIEND_ACCEPTED
            )
        ).use { c ->
            while (c.moveToNext()) {
                out += FriendRow(
                    friendshipId = c.getLong(0),
                    otherUserId = c.getLong(1),
                    otherUserName = c.getString(2),
                    otherUserEmail = c.getString(3),
                    status = c.getString(4),
                    createdAt = c.getLong(5)
                )
            }
        }
        return out
    }

    fun acceptRequest(friendshipId: Long, currentUserId: Long): Int {
        val cv = ContentValues().apply {
            put(DbContract.F_STATUS, DbContract.FRIEND_ACCEPTED)
            put(DbContract.F_RESPONDED_AT, System.currentTimeMillis())
        }
        return writable.update(
            DbContract.T_FRIENDSHIPS,
            cv,
            "${DbContract.F_ID} = ? AND ${DbContract.F_ADDRESSEE_USER_ID} = ? AND ${DbContract.F_STATUS} = ?",
            arrayOf(friendshipId.toString(), currentUserId.toString(), DbContract.FRIEND_PENDING)
        )
    }

    fun declineRequest(friendshipId: Long, currentUserId: Long): Int {
        val cv = ContentValues().apply {
            put(DbContract.F_STATUS, DbContract.FRIEND_DECLINED)
            put(DbContract.F_RESPONDED_AT, System.currentTimeMillis())
        }
        return writable.update(
            DbContract.T_FRIENDSHIPS,
            cv,
            "${DbContract.F_ID} = ? AND ${DbContract.F_ADDRESSEE_USER_ID} = ? AND ${DbContract.F_STATUS} = ?",
            arrayOf(friendshipId.toString(), currentUserId.toString(), DbContract.FRIEND_PENDING)
        )
    }

    fun cancelSentRequest(friendshipId: Long, currentUserId: Long): Int {
        return writable.delete(
            DbContract.T_FRIENDSHIPS,
            "${DbContract.F_ID} = ? AND ${DbContract.F_REQUESTER_USER_ID} = ? AND ${DbContract.F_STATUS} = ?",
            arrayOf(friendshipId.toString(), currentUserId.toString(), DbContract.FRIEND_PENDING)
        )
    }

    fun removeFriend(friendshipId: Long, currentUserId: Long): Int {
        return writable.delete(
            DbContract.T_FRIENDSHIPS,
            """
            ${DbContract.F_ID} = ?
            AND (${DbContract.F_REQUESTER_USER_ID} = ? OR ${DbContract.F_ADDRESSEE_USER_ID} = ?)
            AND ${DbContract.F_STATUS} = ?
            """.trimIndent(),
            arrayOf(
                friendshipId.toString(),
                currentUserId.toString(),
                currentUserId.toString(),
                DbContract.FRIEND_ACCEPTED
            )
        )
    }

    fun getFriendCount(userId: Long): Int {
        readable.rawQuery(
            """
            SELECT COUNT(*)
            FROM ${DbContract.T_FRIENDSHIPS}
            WHERE (${DbContract.F_REQUESTER_USER_ID} = ? OR ${DbContract.F_ADDRESSEE_USER_ID} = ?)
              AND ${DbContract.F_STATUS} = ?
            """.trimIndent(),
            arrayOf(userId.toString(), userId.toString(), DbContract.FRIEND_ACCEPTED)
        ).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    private fun hasActiveRelationship(userA: Long, userB: Long): Boolean {
        readable.rawQuery(
            """
            SELECT 1
            FROM ${DbContract.T_FRIENDSHIPS}
            WHERE (
                    (${DbContract.F_REQUESTER_USER_ID} = ? AND ${DbContract.F_ADDRESSEE_USER_ID} = ?)
                    OR
                    (${DbContract.F_REQUESTER_USER_ID} = ? AND ${DbContract.F_ADDRESSEE_USER_ID} = ?)
                  )
              AND ${DbContract.F_STATUS} IN (?, ?)
            LIMIT 1
            """.trimIndent(),
            arrayOf(
                userA.toString(),
                userB.toString(),
                userB.toString(),
                userA.toString(),
                DbContract.FRIEND_PENDING,
                DbContract.FRIEND_ACCEPTED
            )
        ).use { c ->
            return c.moveToFirst()
        }
    }
}