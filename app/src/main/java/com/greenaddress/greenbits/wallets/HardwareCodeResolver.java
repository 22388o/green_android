package com.greenaddress.greenbits.wallets;

import android.util.Log;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.blockstream.gdk.HardwareWalletResolver;
import com.blockstream.gdk.data.DeviceRequiredData;
import com.fasterxml.jackson.databind.JsonNode;
import com.greenaddress.greenapi.HWWallet;
import com.greenaddress.greenapi.HWWalletBridge;
import com.greenaddress.greenapi.Session;
import com.greenaddress.greenapi.data.BlindedScriptsData;
import com.greenaddress.greenapi.data.HWDeviceRequiredData;
import com.greenaddress.greenapi.data.HardwareCodeResolverData;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.reactivex.rxjava3.core.Single;
import kotlin.jvm.Throws;


public class HardwareCodeResolver implements HardwareWalletResolver {
    private final static String TAG = "HWC";
    private HWWallet hwWallet;

    @Nullable
    private HWWalletBridge parent;
    private final Map<Pair<String, String>, String> mNoncesCache = new ConcurrentHashMap<>();

    @Deprecated
    public HardwareCodeResolver(final HWWalletBridge hwWalletBridge) {
        this.parent = hwWalletBridge;
        this.hwWallet = Session.getSession().getHWWallet();
    }

    public HardwareCodeResolver(final HWWalletBridge hwWalletBridge, final HWWallet hwWallet) {
        this.parent = hwWalletBridge;
        this.hwWallet = hwWallet;
    }

    public HardwareCodeResolver(final HWWallet hwWallet) {
        this.hwWallet = hwWallet;
    }

    @NotNull
    @Override
    public Single<String> requestDataFromDeviceV3(@NotNull HWDeviceRequiredData requiredData) {
        return Single.create(emitter -> {
            try {
                String data = requestDataFromHardware(requiredData);
                if(data != null){
                    emitter.onSuccess(data);
                }else{
                    emitter.tryOnError(new Exception("Unknown error"));
                }

            }catch (Exception e){
                emitter.tryOnError(e);
            }
        });
    }

    @NotNull
    @Override
    public Single<String> requestDataFromDevice(@NotNull DeviceRequiredData requiredData) {
        return null;
    }
    
    public synchronized String requestDataFromHardware(final HWDeviceRequiredData requiredData) {
        final HardwareCodeResolverData data = new HardwareCodeResolverData();

        final List<String> blindingPublicKeys;
        final List<String> scripts;
        final List<String> publicKeys;
        final List<String> nonces;

        switch (requiredData.getAction()) {

        case "get_xpubs":
            final List<String> xpubs = hwWallet.getXpubs(parent, requiredData.getPaths());
            data.setXpubs(xpubs);
            break;

        case "sign_message":
            final HWWallet.SignMsgResult signResult = hwWallet.signMessage(parent, requiredData.getPath(), requiredData.getMessage(),
                    requiredData.getUseAeProtocol(), requiredData.getAeHostCommitment(), requiredData.getAeHostEntropy());
            data.setSignerCommitment(signResult.getSignerCommitment());
            data.setSignature(signResult.getSignature());

            // Corrupt the commitments to emulate a corrupted wallet
            if(hwWallet.getHardwareEmulator() != null && hwWallet.getHardwareEmulator().getAntiExfilCorruptionForMessageSign()){
                // Make it random to allow proceeding to a logged in state
                if(signResult.getSignerCommitment() != null) {
                    // Corrupt the commitment
                    data.setSignerCommitment(signResult.getSignerCommitment().replace("0", "1"));
                }
            }
            break;

        case "sign_tx":
            final HWWallet.SignTxResult result;
            if (hwWallet.getNetwork().isLiquid()) {
                result  = hwWallet.signLiquidTransaction(parent, requiredData.getTransaction(),
                                                               requiredData.getSigningInputs(),
                                                               requiredData.getTransactionOutputs(),
                                                               requiredData.getSigningTransactions(),
                                                               requiredData.getSigningAddressTypes(),
                                                               requiredData.getUseAeProtocol());
                data.setAssetCommitments(result.getAssetCommitments());
                data.setValueCommitments(result.getValueCommitments());
                data.setAssetblinders(result.getAssetBlinders());
                data.setAmountblinders(result.getAmountBlinders());
            } else {
                result = hwWallet.signTransaction(parent, requiredData.getTransaction(),
                                                          requiredData.getSigningInputs(),
                                                          requiredData.getTransactionOutputs(),
                                                          requiredData.getSigningTransactions(),
                                                          requiredData.getSigningAddressTypes(),
                                                          requiredData.getUseAeProtocol());
            }
            data.setSignatures(result.getSignatures());
            data.setSignerCommitments(result.getSignerCommitments());

            // Corrupt the commitments to emulate a corrupted wallet
            if(hwWallet.getHardwareEmulator() != null && hwWallet.getHardwareEmulator().getAntiExfilCorruptionForTxSign()){
                if(result.getSignatures() != null) {
                    // Corrupt the first commitment
                    final ArrayList<String> corrupted = new ArrayList<>(result.getSignerCommitments());
                    corrupted.set(0, corrupted.get(0).replace("0", "1"));
                    data.setSignerCommitments(corrupted);
                }
            }

            break;

        case "get_master_blinding_key":
            data.setMasterBlindingKey(hwWallet.getMasterBlindingKey(parent));
            break;

        case "get_blinding_nonces":
            blindingPublicKeys = new ArrayList<>();
            nonces = new ArrayList<>();
            scripts = requiredData.getScripts();
            publicKeys = requiredData.getPublicKeys();

            if(scripts != null && publicKeys != null && scripts.size() == publicKeys.size()){
                for(int i = 0; i < scripts.size(); i++){

                    if (!mNoncesCache.containsKey(Pair.create(publicKeys.get(i), scripts.get(i)))) {
                        final String nonce = hwWallet.getBlindingNonce(parent, publicKeys.get(i), scripts.get(i));
                        mNoncesCache.put(Pair.create(publicKeys.get(i), scripts.get(i)), nonce);
                        nonces.add(nonce);
                    } else {
                        nonces.add(mNoncesCache.get(Pair.create(publicKeys.get(i), scripts.get(i))));
                    }

                    if(requiredData.getBlindingKeysRequired()){
                        blindingPublicKeys.add(hwWallet.getBlindingKey(parent, scripts.get(i)));
                    }
                }
            }

            data.setNonces(nonces);

            if(requiredData.getBlindingKeysRequired()){
                data.setPublicKeys(blindingPublicKeys);
            }

            break;

        case "get_blinding_public_keys":
            scripts = requiredData.getScripts();
            blindingPublicKeys = new ArrayList<>();

            if(scripts != null){
                for(String script : scripts){
                    blindingPublicKeys.add(hwWallet.getBlindingKey(parent, script));
                }
            }

            data.setPublicKeys(blindingPublicKeys);
            break;

        default:
            Log.w(TAG, "unknown action " + requiredData.getAction());
            return null;
        }
        return data.toString();
    }
}
