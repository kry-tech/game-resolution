package com.gameresolution;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class PerfilAdapter extends RecyclerView.Adapter<PerfilAdapter.ViewHolder> {

    List<Perfil> perfis;
    OnPerfilClickListener listener;

    public interface OnPerfilClickListener {
        void onPlayClick(Perfil perfil, int position);
        void onDeleteClick(Perfil perfil, int position);
    }

    public PerfilAdapter(List<Perfil> perfis, OnPerfilClickListener listener) {
        this.perfis = perfis;
        this.listener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_perfil, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Perfil p = perfis.get(position);
        holder.tvNome.setText(p.nome);
        holder.tvPacote.setText(p.pacote);
        holder.tvResolucao.setText(p.largura + "x" + p.altura + " | DPI: " + p.dpi);

        holder.btnPlay.setText(p.ativo ? "⏹" : "▶");
        holder.btnPlay.setBackgroundTint(
            holder.itemView.getContext().getResources()
                .getColor(p.ativo ? android.R.color.holo_red_dark : android.R.color.holo_green_dark)
        );

        holder.btnPlay.setOnClickListener(v -> listener.onPlayClick(p, position));
        holder.btnExcluir.setOnClickListener(v -> listener.onDeleteClick(p, position));
    }

    @Override
    public int getItemCount() {
        return perfis.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNome, tvPacote, tvResolucao;
        Button btnPlay, btnExcluir;

        ViewHolder(View itemView) {
            super(itemView);
            tvNome = itemView.findViewById(R.id.tv_nome_perfil);
            tvPacote = itemView.findViewById(R.id.tv_pacote);
            tvResolucao = itemView.findViewById(R.id.tv_resolucao);
            btnPlay = itemView.findViewById(R.id.btn_play);
            btnExcluir = itemView.findViewById(R.id.btn_excluir);
        }
    }
}
