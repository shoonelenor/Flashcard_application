package com.example.stardeckapplication.ui.profile

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.stardeckapplication.R
import com.example.stardeckapplication.db.DbContract
import com.example.stardeckapplication.db.FriendDao
import com.example.stardeckapplication.db.StarDeckDbHelper
import com.example.stardeckapplication.util.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class FriendsActivity : AppCompatActivity() {

    private lateinit var session: SessionManager
    private lateinit var dao: FriendDao

    private lateinit var etSearch: EditText
    private lateinit var btnSearch: Button
    private lateinit var btnTabFriends: Button
    private lateinit var btnTabRequests: Button
    private lateinit var btnTabFind: Button
    private lateinit var tvCount: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var listView: ListView

    private var currentUserId: Long = -1L
    private var currentMode: String = MODE_FRIENDS

    private var friends  = listOf<FriendDao.FriendRow>()
    private var incoming = listOf<FriendDao.FriendRow>()
    private var sent     = listOf<FriendDao.FriendRow>()
    private var foundUsers = listOf<FriendDao.UserLite>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friends)

        supportActionBar?.apply {
            title = "Friends"
            setDisplayHomeAsUpEnabled(true)
        }

        session = SessionManager(this)
        dao     = FriendDao(StarDeckDbHelper(this))

        val me = session.load()
        if (me == null || me.role != DbContract.ROLE_USER) {
            finish()
            return
        }
        currentUserId = me.id.toLong()

        etSearch       = findViewById(R.id.etFriendSearch)
        btnSearch      = findViewById(R.id.btnFriendSearch)
        btnTabFriends  = findViewById(R.id.btnTabFriends)
        btnTabRequests = findViewById(R.id.btnTabRequests)
        btnTabFind     = findViewById(R.id.btnTabFind)
        tvCount        = findViewById(R.id.tvFriendCount)
        tvEmpty        = findViewById(R.id.tvFriendEmpty)
        listView       = findViewById(R.id.listFriends)

        btnTabFriends.setOnClickListener  { currentMode = MODE_FRIENDS;  refreshAll() }
        btnTabRequests.setOnClickListener { currentMode = MODE_REQUESTS; refreshAll() }
        btnTabFind.setOnClickListener     { currentMode = MODE_FIND;     refreshAll() }
        btnSearch.setOnClickListener      { currentMode = MODE_FIND;     refreshAll() }

        listView.setOnItemClickListener { _, _, position, _ ->
            when (currentMode) {
                MODE_FRIENDS  -> showFriendActions(friends[position])
                MODE_REQUESTS -> {
                    val totalIncoming = incoming.size
                    if (position < totalIncoming)
                        showIncomingRequestActions(incoming[position])
                    else
                        showSentRequestActions(sent[position - totalIncoming])
                }
                MODE_FIND -> showSearchUserActions(foundUsers[position])
            }
        }

        refreshAll()
    }

    // ── Data ─────────────────────────────────────────────────────────────────

    private fun refreshAll() {
        friends  = dao.getFriends(currentUserId)
        incoming = dao.getIncomingRequests(currentUserId)
        sent     = dao.getSentRequests(currentUserId)
        render()
    }

    // ── Render ────────────────────────────────────────────────────────────────

    private fun render() {
        when (currentMode) {
            MODE_FRIENDS  -> renderFriends()
            MODE_REQUESTS -> renderRequests()
            MODE_FIND     -> renderSearch()
        }
        updateTabState()
    }

    private fun renderFriends() {
        val items = friends.map { "${it.otherUserName}\n${it.otherUserEmail}" }
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        tvCount.text = "Friends: ${friends.size}"
        tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        tvEmpty.text = "You have no friends yet.\nTap \"Find Users\" to search."
    }

    private fun renderRequests() {
        val items = mutableListOf<String>()
        items += incoming.map { "⬇ Incoming: ${it.otherUserName}\n${it.otherUserEmail}" }
        items += sent.map     { "⬆ Sent: ${it.otherUserName}\n${it.otherUserEmail}" }
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        tvCount.text = "Requests: ${incoming.size} incoming · ${sent.size} sent"
        tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        tvEmpty.text = "No pending requests."
    }

    private fun renderSearch() {
        val query = etSearch.text.toString().trim()
        foundUsers = if (query.isBlank()) emptyList()
        else dao.searchUsersForFriend(currentUserId, query)

        val items = foundUsers.map { "${it.name}\n${it.email}" }
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        tvCount.text = when {
            query.isBlank()   -> "Type a name or email to search."
            items.isEmpty()   -> "No users found for \"$query\"."
            else              -> "Found: ${items.size} user(s)"
        }
        tvEmpty.visibility = if (query.isNotBlank() && items.isEmpty()) View.VISIBLE else View.GONE
        tvEmpty.text = "No users found."
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    private fun showFriendActions(row: FriendDao.FriendRow) {
        MaterialAlertDialogBuilder(this)
            .setTitle(row.otherUserName)
            .setMessage(row.otherUserEmail)
            .setPositiveButton("Remove Friend") { _, _ ->
                val result = dao.removeFriend(row.friendshipId, currentUserId)
                toast(if (result > 0) "Friend removed." else "Could not remove friend.")
                if (result > 0) refreshAll()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showIncomingRequestActions(row: FriendDao.FriendRow) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Friend Request from ${row.otherUserName}")
            .setMessage(row.otherUserEmail)
            .setPositiveButton("Accept") { _, _ ->
                val result = dao.acceptRequest(row.friendshipId, currentUserId)
                toast(if (result > 0) "Friend request accepted!" else "Could not accept request.")
                if (result > 0) refreshAll()
            }
            .setNegativeButton("Decline") { _, _ ->
                val result = dao.declineRequest(row.friendshipId, currentUserId)
                toast(if (result > 0) "Request declined." else "Could not decline request.")
                if (result > 0) refreshAll()
            }
            .setNeutralButton("Close", null)
            .show()
    }

    private fun showSentRequestActions(row: FriendDao.FriendRow) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Sent to ${row.otherUserName}")
            .setMessage(row.otherUserEmail)
            .setPositiveButton("Cancel Request") { _, _ ->
                val result = dao.cancelSentRequest(row.friendshipId, currentUserId)
                toast(if (result > 0) "Request cancelled." else "Could not cancel request.")
                if (result > 0) refreshAll()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showSearchUserActions(user: FriendDao.UserLite) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Add ${user.name}?")
            .setMessage(user.email)
            .setPositiveButton("Send Friend Request") { _, _ ->
                val result = dao.sendFriendRequest(currentUserId, user.id)
                toast(if (result > 0) "Friend request sent!" else "Could not send request.")
                if (result > 0) refreshAll()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun updateTabState() {
        btnTabFriends.isEnabled  = currentMode != MODE_FRIENDS
        btnTabRequests.isEnabled = currentMode != MODE_REQUESTS
        btnTabFind.isEnabled     = currentMode != MODE_FIND
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        private const val MODE_FRIENDS  = "friends"
        private const val MODE_REQUESTS = "requests"
        private const val MODE_FIND     = "find"
    }
}