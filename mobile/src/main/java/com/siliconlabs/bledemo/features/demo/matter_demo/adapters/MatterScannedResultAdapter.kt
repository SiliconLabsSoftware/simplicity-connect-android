package com.siliconlabs.bledemo.features.demo.matter_demo.adapters

import android.content.Context
import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.daimajia.swipe.SwipeLayout
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.databinding.MatterScannedListItemBinding
import com.siliconlabs.bledemo.features.demo.matter_demo.model.MatterScannedResultModel
import com.siliconlabs.bledemo.features.demo.matter_demo.viewholder.MatterItemViewModel
import kotlinx.coroutines.runBlocking


class MatterScannedResultAdapter(
    private val matterList: List<MatterScannedResultModel>
) : RecyclerView.Adapter<MatterItemViewModel>() {
    private lateinit var context: Context
    private var onClickListener: OnClickListener? = null
    private var selectedPosition: Int = RecyclerView.NO_POSITION

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatterItemViewModel {
        val binding =
            MatterScannedListItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        context = parent.context
        return MatterItemViewModel(binding, parent.context)
    }

    override fun onBindViewHolder(holder: MatterItemViewModel, position: Int) {
        val matterInfo = matterList[position]
        holder.bind(matterInfo)

        if (matterInfo.isDeviceOnline) {
            holder.itemView.isEnabled = true
            holder.itemView.isClickable = true

            holder.binding.root.alpha = 1.0f // Set alpha to 1 for enabled rows
        } else {
            holder.binding.root.alpha = 0.5f
            holder.binding.imageView.setColorFilter(
                ContextCompat.getColor(context, R.color.silabs_rebranding_2373_matter_list_disable_color),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
            holder.itemView.isEnabled = false
            holder.itemView.isClickable = false
             // Set alpha to 0.5 for disabled rows
//            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.silabs_inactive_light));
//            holder.binding.cardView.setBackgroundColor(ContextCompat.getColor(context, R.color.silabs_inactive_light));
//            holder.binding.itemViewHolder.setBackgroundColor(ContextCompat.getColor(context, R.color.silabs_inactive_light));
//            holder.binding.imageView.setBackgroundColor(ContextCompat.getColor(context, R.color.silabs_inactive_light));
        }

        holder.binding.swipe.setShowMode(SwipeLayout.ShowMode.LayDown);
        holder.binding.swipe.addDrag(
            SwipeLayout.DragEdge.Right,
            holder.binding.swipe.findViewById(R.id.bottom_wrapper)
        )

        holder.binding.itemViewHolder.setOnClickListener {
            if (matterInfo.isDeviceOnline){
                runBlocking {
                    if (onClickListener != null) {
                        onClickListener!!.onClick(position, matterInfo)
                    }
                }
            }else{
                return@setOnClickListener
            }
        }
        holder.binding.imgDelete.setOnClickListener {
            runBlocking {
                if (onClickListener != null) {
                    onClickListener!!.onDelete(position, matterInfo)
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return matterList.size
    }

    fun setOnClickListener(onClickListener: OnClickListener) {
        this.onClickListener = onClickListener
    }

    interface OnClickListener {
        suspend fun onClick(position: Int, model: MatterScannedResultModel)

        suspend fun onDelete(position: Int, model: MatterScannedResultModel)
    }


}
