package com.example.multi_barcode_scan_android_xml

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SingleBarCodeAdapter(
    private val context: Context?,
    private var listOfValues: MutableList<String>,
    private val barcodeClickListener: BarcodeClickListener,

) : RecyclerView.Adapter<SingleBarCodeAdapter.ViewHolder>() {

    /**
     * Find all the views of the list item
     */
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        lateinit var jobLabel: TextView
        val separator: View = itemView.findViewById(R.id.separator)

        /**
         * Show the data in the views
         */
        fun bindView(item: String, position: Int, barcodeClickListener: BarcodeClickListener, hideSeparator: Boolean) {
            jobLabel = itemView.findViewById(R.id.job_name)

            jobLabel.text = item

            itemView.setOnClickListener {
                barcodeClickListener.onItemClick(position, item)
            }

            separator.visibility = if (hideSeparator) View.INVISIBLE else View.VISIBLE
        }
    }

    override fun getItemCount(): Int = listOfValues.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = listOfValues[position]
        holder.bindView(item, position, barcodeClickListener, position.equals(listOfValues.size - 1))
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView: View = LayoutInflater.from(context).inflate(R.layout.single_barcode_item, parent, false)
        return ViewHolder(itemView)
    }
}
