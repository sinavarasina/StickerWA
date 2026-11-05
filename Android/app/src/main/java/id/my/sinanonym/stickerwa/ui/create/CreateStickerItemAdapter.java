package id.my.sinanonym.stickerwa.ui.create;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import id.my.sinanonym.stickerwa.R;

public class CreateStickerItemAdapter extends RecyclerView.Adapter<CreateStickerItemAdapter.ViewHolder> {

    public interface OnRemoveClickListener {
        void onRemove(int position);
    }

    private final List<Uri> stickerUris;
    private final OnRemoveClickListener removeListener;

    public CreateStickerItemAdapter(List<Uri> stickerUris, OnRemoveClickListener removeListener) {
        this.stickerUris = stickerUris;
        this.removeListener = removeListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_create_sticker_thumb, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Uri uri = stickerUris.get(position);
        holder.thumb.setImageURI(uri);
        holder.btnRemove.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && removeListener != null) {
                removeListener.onRemove(pos);
            }
        });
    }

    @Override
    public int getItemCount() {
        return stickerUris.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final View btnRemove;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            thumb = itemView.findViewById(R.id.sticker_thumb);
            btnRemove = itemView.findViewById(R.id.btn_remove_sticker);
        }
    }
}
