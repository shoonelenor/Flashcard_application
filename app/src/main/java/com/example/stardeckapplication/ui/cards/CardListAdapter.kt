package com.example.stardeckapplication.ui.cards

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.stardeckapplication.R
import com.example.stardeckapplication.db.CardDao.Card
import java.io.File

class CardListAdapter(
    private val cards: List<Card>,
    private val onEdit: (Card) -> Unit,
    private val onDelete: (Card) -> Unit
) : RecyclerView.Adapter<CardListAdapter.CardViewHolder>() {

    inner class CardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvFront: TextView      = view.findViewById(R.id.tvCardFront)
        val tvBack: TextView       = view.findViewById(R.id.tvCardBack)
        val imgFront: ImageView    = view.findViewById(R.id.imgCardFront)
        val imgBack: ImageView     = view.findViewById(R.id.imgCardBack)
        val btnEdit: ImageButton   = view.findViewById(R.id.btnEditCard)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteCard)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_card, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val card = cards[position]
        holder.tvFront.text = card.front
        holder.tvBack.text  = card.back

        // load front image
        val frontFile = card.frontImagePath?.let { File(it) }
        if (frontFile != null && frontFile.exists()) {
            holder.imgFront.setImageURI(Uri.fromFile(frontFile))
            holder.imgFront.visibility = View.VISIBLE
        } else {
            holder.imgFront.visibility = View.GONE
        }

        // load back image
        val backFile = card.backImagePath?.let { File(it) }
        if (backFile != null && backFile.exists()) {
            holder.imgBack.setImageURI(Uri.fromFile(backFile))
            holder.imgBack.visibility = View.VISIBLE
        } else {
            holder.imgBack.visibility = View.GONE
        }

        holder.btnEdit.setOnClickListener   { onEdit(card) }
        holder.btnDelete.setOnClickListener { onDelete(card) }
    }

    override fun getItemCount(): Int = cards.size
}
