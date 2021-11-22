package com.greenaddress.greenbits.ui.assets;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.greenaddress.greenapi.data.AssetInfoData;
import com.greenaddress.greenapi.data.EntityData;
import com.greenaddress.greenapi.data.NetworkData;
import com.greenaddress.greenapi.model.Conversion;
import com.greenaddress.greenbits.ui.GaActivity;
import com.greenaddress.greenbits.ui.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AssetsAdapter extends RecyclerView.Adapter<AssetsAdapter.Item> {

    private final Map<String, Long> mAssets;
    private final List<String> mAssetsIds;
    private final OnAssetSelected mOnAccountSelected;
    private final NetworkData mNetworkData;
    private final GaActivity mActivity;

    @FunctionalInterface
    public interface OnAssetSelected {
        void onAssetSelected(String assetSelected);
    }

    public AssetsAdapter(final GaActivity activity,
                         final Map<String, Long> assets,
                         final NetworkData networkData,
                         final OnAssetSelected cb) {
        mActivity = activity;
        mAssets = assets;
        mOnAccountSelected = cb;
        mNetworkData = networkData;
        mAssetsIds = new ArrayList<>(mAssets.keySet());
        if (mAssetsIds.contains(mNetworkData.getPolicyAsset())) {
            // Move btc as first in the list
            mAssetsIds.remove(mNetworkData.getPolicyAsset());
            mAssetsIds.add(0,mNetworkData.getPolicyAsset());
        }
    }

    @Override
    public Item onCreateViewHolder(final ViewGroup parent, final int viewType) {
        final View view = LayoutInflater.from(parent.getContext())
                          .inflate(R.layout.list_element_asset, parent, false);
        return new Item(view);
    }

    @Override
    public void onBindViewHolder(final Item holder, final int position) {
        final String assetId = mAssetsIds.get(position);
        final boolean isBTC = mNetworkData.getPolicyAsset().equals(assetId);
        final boolean isLiquid = mNetworkData.getLiquid();
        final boolean isTestnet = mNetworkData.isTestnet();
        final Long satoshi = mAssets.get(assetId);
        final AssetInfoData assetInfo = mActivity.getSession().getRegistry().getAssetInfo(assetId);
        if (mOnAccountSelected != null)
            holder.mAssetLayout.setOnClickListener(v -> mOnAccountSelected.onAssetSelected(assetId));

        if(isLiquid){
            if (isBTC) { // Liquid
                holder.mAssetName.setText("Liquid Bitcoin");
                holder.mAssetDomain.setVisibility(View.GONE);
                try {
                    holder.mAssetValue.setText(Conversion.getBtc(mActivity.getSession(), satoshi, true));
                } catch (final Exception e) {
                    Log.e("", "Conversion error: " + e.getLocalizedMessage());
                }
            } else {
                holder.mAssetName.setText(assetInfo != null ? assetInfo.getName() : assetId);
                final EntityData entity = assetInfo != null ? assetInfo.getEntity() : null;
                if (entity != null && entity.getDomain() != null && !entity.getDomain().isEmpty()) {
                    holder.mAssetDomain.setVisibility(View.VISIBLE);
                    holder.mAssetDomain.setText(entity.getDomain());
                } else {
                    holder.mAssetDomain.setVisibility(View.GONE);
                }
                try {
                    holder.mAssetValue.setText(Conversion.getAsset(mActivity.getSession(), satoshi, assetId, assetInfo, true));
                } catch (final Exception e) {
                    e.printStackTrace();
                }
            }
            // Get l-btc & asset icon from asset icon map
            final String asset = isBTC ? mNetworkData.getPolicyAsset() : assetId;
            holder.mAssetIcon.setImageDrawable(mActivity.getSession().getRegistry().getAssetManager().getAssetDrawableOrDefault(asset));
        }else{
            if(isTestnet){
                holder.mAssetName.setText("Testnet Bitcoin");
                holder.mAssetIcon.setImageResource(R.drawable.ic_testnet_btc);
            }else{
                holder.mAssetName.setText("Bitcoin");
                holder.mAssetIcon.setImageResource(R.drawable.ic_btc);
            }
            holder.mAssetDomain.setVisibility(View.GONE);
            try {
                holder.mAssetValue.setText(Conversion.getBtc(mActivity.getSession(), satoshi, true));
            } catch (final Exception e) {
                Log.e("", "Conversion error: " + e.getLocalizedMessage());
            }
        }
    }

    @Override
    public int getItemCount() {
        return mAssets.size();
    }

    static class Item extends RecyclerView.ViewHolder {

        final LinearLayout mAssetLayout;
        final TextView mAssetName;
        final TextView mAssetDomain;
        final TextView mAssetValue;
        final ImageView mAssetIcon;

        Item(final View v) {
            super(v);
            mAssetLayout = v.findViewById(R.id.assetLayout);
            mAssetName = v.findViewById(R.id.assetName);
            mAssetDomain = v.findViewById(R.id.assetDomain);
            mAssetValue = v.findViewById(R.id.assetValue);
            mAssetIcon = v.findViewById(R.id.assetIcon);
        }
    }
}
