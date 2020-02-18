package com.ironsource.adapters.vungle;

import android.app.Activity;
import android.text.TextUtils;
import com.ironsource.mediationsdk.AbstractAdapter;
import com.ironsource.mediationsdk.IntegrationData;
import com.ironsource.mediationsdk.logger.IronSourceLogger;
import com.ironsource.mediationsdk.logger.IronSourceLogger.IronSourceTag;
import com.ironsource.mediationsdk.sdk.InterstitialSmashListener;
import com.ironsource.mediationsdk.sdk.RewardedVideoSmashListener;
import com.ironsource.mediationsdk.utils.ErrorBuilder;
import com.vungle.warren.AdConfig;
import com.vungle.warren.InitCallback;
import com.vungle.warren.LoadAdCallback;
import com.vungle.warren.PlayAdCallback;
import com.vungle.warren.Vungle;
import com.vungle.warren.Vungle.Consent;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
import com.vungle.warren.error.VungleException;

class VungleAdapter
        extends AbstractAdapter
{
    private static final String VERSION = "4.1.6";
    private static final String GitHash = "e4a4735e4";
    private static final String APP_ID = "AppID";
    private static final String PLACEMENT_ID = "PlacementId";
    private static final String CONSENT_MESSAGE_VERSION = "1.0.0";

    private static enum EInitState
    {
        NOT_INIT,  INIT_IN_PROGRESS,  INIT_SUCCESS,  INIT_FAIL;

        private EInitState() {}
    }

    private EInitState mInitState = EInitState.NOT_INIT;
    private Set<String> mInitiatedAdUnits;
    private Boolean mIsConsent;
    private AtomicBoolean mInitCalled;
    private ConcurrentHashMap<String, RewardedVideoSmashListener> mPlacementIdToRewardedVideoSmashListener;
    private ConcurrentHashMap<String, InterstitialSmashListener> mPlacementIdToInterstitialSmashListener;

    public static VungleAdapter startAdapter(String providerName)
    {
        return new VungleAdapter(providerName);
    }

    private VungleAdapter(String providerName)
    {
        super(providerName);
        this.mIsConsent = null;
        this.mInitCalled = new AtomicBoolean(false);
        this.mPlacementIdToRewardedVideoSmashListener = new ConcurrentHashMap();
        this.mPlacementIdToInterstitialSmashListener = new ConcurrentHashMap();
    }

    public static IntegrationData getIntegrationData(Activity activity)
    {
        IntegrationData ret = new IntegrationData("Vungle", VERSION);
        ret.validateWriteExternalStorage = true;

        return ret;
    }

    public String getVersion()
    {
        return VERSION;
    }

    public static String getAdapterSDKVersion()
    {
        return "6.5.2";
    }

    public String getCoreSDKVersion()
    {
        return getAdapterSDKVersion();
    }

    protected void setConsent(boolean consent)
    {
        if (getCurrentInitState() == EInitState.INIT_SUCCESS) {
            Vungle.updateConsentStatus(consent ? Vungle.Consent.OPTED_IN : Vungle.Consent.OPTED_OUT, "1.0.0");
        } else {
            this.mIsConsent = Boolean.valueOf(consent);
        }
    }

    public void onResume(Activity activity) {}

    public void onPause(Activity activity) {}

    public void initRewardedVideo(Activity activity, String appKey, String userId, JSONObject config, RewardedVideoSmashListener listener)
    {
        if ((TextUtils.isEmpty(config.optString("AppID"))) || (TextUtils.isEmpty(config.optString("PlacementId"))))
        {
            if (listener != null) {
                listener.onRewardedVideoAvailabilityChanged(false);
            }
            return;
        }
        if ((!TextUtils.isEmpty(config.optString("PlacementId"))) && (listener != null)) {
            this.mPlacementIdToRewardedVideoSmashListener.put(config.optString("PlacementId"), listener);
        }
        addInitiatedAdUnit("Rewarded Video");
        switch (getCurrentInitState())
        {
            case NOT_INIT:
                initVungleSdk(activity, config.optString("AppID"));
                break;
            case INIT_IN_PROGRESS:
                break;
            case INIT_SUCCESS:
                if (Vungle.canPlayAd(config.optString("PlacementId")))
                {
                    if (listener != null) {
                        listener.onRewardedVideoAvailabilityChanged(true);
                    }
                }
                else {
                    loadRewardedVideoAd(config.optString("PlacementId"));
                }
                break;
            case INIT_FAIL:
                if (listener != null) {
                    listener.onRewardedVideoAvailabilityChanged(false);
                }
                break;
        }
    }

    public void fetchRewardedVideo(JSONObject config)
    {
        String placementId = config.optString("PlacementId");
        log(IronSourceLogger.IronSourceTag.ADAPTER_API, getProviderName() + ": in fetchRewardedVideo for placementId " + placementId, 0);
        if (!TextUtils.isEmpty(placementId)) {
            if (Vungle.canPlayAd(placementId)) {
                ((RewardedVideoSmashListener)this.mPlacementIdToRewardedVideoSmashListener.get(placementId)).onRewardedVideoAvailabilityChanged(true);
            } else if (this.mPlacementIdToRewardedVideoSmashListener.containsKey(placementId)) {
                loadRewardedVideoAd(placementId);
            }
        }
    }

    public void showRewardedVideo(JSONObject config, final RewardedVideoSmashListener listener)
    {
        AdConfig overrideConfig = new AdConfig();
        if (Vungle.canPlayAd(config.optString("PlacementId")))
        {
            if (!TextUtils.isEmpty(getDynamicUserId())) {
                Vungle.setIncentivizedFields(getDynamicUserId(), null, null, null, null);
            }
            Vungle.playAd(config.optString("PlacementId"), overrideConfig, new PlayAdCallback()
            {
                public void onAdStart(String placementReferenceId)
                {
                    VungleAdapter.this.log(IronSourceLogger.IronSourceTag.ADAPTER_API, VungleAdapter.this.getProviderName() + ": RewardedVideo ad started for placementReferenceId: " + placementReferenceId, 1);
                    if (listener != null)
                    {
                        listener.onRewardedVideoAdOpened();
                        listener.onRewardedVideoAdStarted();
                    }
                }

                public void onAdEnd(String placementReferenceId, boolean completed, boolean isCTAClicked)
                {
                    VungleAdapter.this.log(IronSourceLogger.IronSourceTag.ADAPTER_API, VungleAdapter.this.getProviderName() + ": RewardedVideo ad ended for placementReferenceId: " + placementReferenceId, 1);
                    if (listener != null)
                    {
                        if (isCTAClicked) {
                            listener.onRewardedVideoAdClicked();
                        }
                        listener.onRewardedVideoAdEnded();
                        if (completed) {
                            listener.onRewardedVideoAdRewarded();
                        }
                        listener.onRewardedVideoAdClosed();
                    }
                }

                public void onError(String placementReferenceId, VungleException vungleException)
                {
                    VungleAdapter.this.log(IronSourceLogger.IronSourceTag.ADAPTER_API, VungleAdapter.this.getProviderName() + ": RewardedVideo ad failed to show for placementReferenceId: " + placementReferenceId + "error: " + vungleException.getLocalizedMessage(), 1);
                    if (listener != null)
                    {
                        listener.onRewardedVideoAdShowFailed(ErrorBuilder.buildNoAdsToShowError("Rewarded Video"));
                        listener.onRewardedVideoAvailabilityChanged(false);
                    }
                }
            });
        }
    }

    public boolean isRewardedVideoAvailable(JSONObject config)
    {
        return (Vungle.isInitialized()) && (Vungle.canPlayAd(config.optString("PlacementId")));
    }

    public void initInterstitial(Activity activity, String appKey, String userId, JSONObject config, InterstitialSmashListener listener)
    {
        if ((TextUtils.isEmpty(config.optString("AppID"))) || (TextUtils.isEmpty(config.optString("PlacementId"))))
        {
            if (listener != null) {
                listener.onInterstitialInitFailed(ErrorBuilder.buildInitFailedError("Missing params", "Interstitial"));
            }
            return;
        }
        if ((!TextUtils.isEmpty(config.optString("PlacementId"))) && (listener != null)) {
            this.mPlacementIdToInterstitialSmashListener.put(config.optString("PlacementId"), listener);
        }
        addInitiatedAdUnit("Interstitial");
        HashSet<String> allPlacements = new HashSet();
        switch (getCurrentInitState())
        {
            case NOT_INIT:
                initVungleSdk(activity, config.optString("AppID"));
                break;
            case INIT_IN_PROGRESS:
                break;
            case INIT_SUCCESS:
                if (listener != null) {
                    listener.onInterstitialInitSuccess();
                }
                break;
            case INIT_FAIL:
                if (listener != null) {
                    listener.onInterstitialInitFailed(ErrorBuilder.buildInitFailedError("Init Failed", "Interstitial"));
                }
                break;
        }
    }

    public void loadInterstitial(JSONObject config, final InterstitialSmashListener listener)
    {
        if (Vungle.isInitialized())
        {
            String placementAd = config.optString("PlacementId");
            if (Vungle.canPlayAd(placementAd))
            {
                if (listener != null) {
                    listener.onInterstitialAdReady();
                }
            }
            else {
                Vungle.loadAd(placementAd, new LoadAdCallback()
                {
                    public void onAdLoad(String placementReferenceId)
                    {
                        VungleAdapter.this.log(IronSourceLogger.IronSourceTag.ADAPTER_API, VungleAdapter.this.getProviderName() + ": Interstitial loaded for placementReferenceId: " + placementReferenceId, 1);
                        if (listener != null) {
                            listener.onInterstitialAdReady();
                        }
                    }

                    public void onError(String placementReferenceId, VungleException vungleException)
                    {
                        VungleAdapter.this.log(IronSourceLogger.IronSourceTag.ADAPTER_API, VungleAdapter.this.getProviderName() + ": Interstitial failed to load for placementReferenceId: " + placementReferenceId + " ,error: " + vungleException.getLocalizedMessage(), 1);
                        if (listener != null) {
                            listener.onInterstitialAdLoadFailed(ErrorBuilder.buildLoadFailedError("Error loading Ad: " + vungleException.getLocalizedMessage()));
                        }
                    }
                });
            }
        }
    }

    public void showInterstitial(JSONObject config, final InterstitialSmashListener listener)
    {
        if (Vungle.canPlayAd(config.optString("PlacementId"))) {
            Vungle.playAd(config.optString("PlacementId"), new AdConfig(), new PlayAdCallback()
            {
                public void onAdStart(String placementReferenceId)
                {
                    VungleAdapter.this.log(IronSourceLogger.IronSourceTag.ADAPTER_API, VungleAdapter.this.getProviderName() + ": Interstitial ad started for placementReferenceId: " + placementReferenceId, 1);
                    if (listener != null)
                    {
                        listener.onInterstitialAdOpened();
                        listener.onInterstitialAdShowSucceeded();
                    }
                }

                public void onAdEnd(String placementReferenceId, boolean completed, boolean isCTAClicked)
                {
                    VungleAdapter.this.log(IronSourceLogger.IronSourceTag.ADAPTER_API, VungleAdapter.this.getProviderName() + ": Interstitial ad ended for placementReferenceId: " + placementReferenceId, 1);
                    if (listener != null)
                    {
                        if (isCTAClicked) {
                            listener.onInterstitialAdClicked();
                        }
                        listener.onInterstitialAdClosed();
                    }
                }

                public void onError(String placementReferenceId, VungleException vungleException)
                {
                    VungleAdapter.this.log(IronSourceLogger.IronSourceTag.ADAPTER_API, VungleAdapter.this.getProviderName() + ": Interstitial ad failed to show for placementReferenceId: " + placementReferenceId + "error: " + vungleException.getLocalizedMessage(), 1);
                    if (listener != null) {
                        listener.onInterstitialAdShowFailed(ErrorBuilder.buildNoAdsToShowError("Interstitial"));
                    }
                }
            });
        } else if (listener != null) {
            listener.onInterstitialAdShowFailed(ErrorBuilder.buildNoAdsToShowError("Interstitial"));
        }
    }

    public boolean isInterstitialReady(JSONObject config)
    {
        return (Vungle.isInitialized()) && (Vungle.canPlayAd(config.optString("PlacementId")));
    }

    private void setInitState(EInitState state)
    {
        log(IronSourceLogger.IronSourceTag.ADAPTER_API, getProviderName() + ":init state changed from " + this.mInitState + " to " + state + ")", 1);

        this.mInitState = state;
    }

    private EInitState getCurrentInitState()
    {
        return this.mInitState;
    }

    private void addInitiatedAdUnit(String adUnit)
    {
        if (this.mInitiatedAdUnits == null) {
            this.mInitiatedAdUnits = new HashSet();
        }
        this.mInitiatedAdUnits.add(adUnit);
    }

    private void initVungleSdk(Activity activity, String appId)
    {
        if (this.mInitCalled.compareAndSet(false, true))
        {
            setInitState(EInitState.INIT_IN_PROGRESS);

            Vungle.init(appId, activity.getApplicationContext(), new InitCallback()
            {
                public void onSuccess()
                {
                    VungleAdapter.this.log(IronSourceLogger.IronSourceTag.ADAPTER_API, VungleAdapter.this.getProviderName() + ": Succeeded to initialize SDK ", 1);

                    VungleAdapter.this.setInitState(VungleAdapter.EInitState.INIT_SUCCESS);
                    if (VungleAdapter.this.mIsConsent != null) {
                        VungleAdapter.this.setConsent(VungleAdapter.this.mIsConsent.booleanValue());
                    }
                    if (VungleAdapter.this.mInitiatedAdUnits != null)
                    {
                        if (VungleAdapter.this.mInitiatedAdUnits.contains("Rewarded Video")) {
                            for (Map.Entry<String, RewardedVideoSmashListener> entry : VungleAdapter.this.mPlacementIdToRewardedVideoSmashListener.entrySet()) {
                                VungleAdapter.this.loadRewardedVideoAd((String)entry.getKey());
                            }
                        }
                        if (VungleAdapter.this.mInitiatedAdUnits.contains("Interstitial")) {
                            for (Map.Entry<String, InterstitialSmashListener> entry : VungleAdapter.this.mPlacementIdToInterstitialSmashListener.entrySet()) {
                                if (entry.getValue() != null) {
                                    ((InterstitialSmashListener)entry.getValue()).onInterstitialInitSuccess();
                                }
                            }
                        }
                    }
                }

                public void onError(VungleException vungleException)
                {
                    VungleAdapter.this.log(IronSourceLogger.IronSourceTag.ADAPTER_API, VungleAdapter.this.getProviderName() + ": Failed to initialize SDK ", 1);

                    VungleAdapter.this.setInitState(VungleAdapter.EInitState.INIT_FAIL);
                    if (VungleAdapter.this.mInitiatedAdUnits != null)
                    {
                        if (VungleAdapter.this.mInitiatedAdUnits.contains("Rewarded Video")) {
                            for (Map.Entry<String, RewardedVideoSmashListener> entry : VungleAdapter.this.mPlacementIdToRewardedVideoSmashListener.entrySet()) {
                                if (entry.getValue() != null) {
                                    ((RewardedVideoSmashListener)entry.getValue()).onRewardedVideoAvailabilityChanged(false);
                                }
                            }
                        }
                        if (VungleAdapter.this.mInitiatedAdUnits.contains("Interstitial")) {
                            for (Map.Entry<String, InterstitialSmashListener> entry : VungleAdapter.this.mPlacementIdToInterstitialSmashListener.entrySet()) {
                                if (entry.getValue() != null) {
                                    ((InterstitialSmashListener)entry.getValue()).onInterstitialInitFailed(ErrorBuilder.buildInitFailedError("Vungle failed to init: " + vungleException.getLocalizedMessage(), "Interstitial"));
                                }
                            }
                        }
                    }
                }

                public void onAutoCacheAdAvailable(String placementId)
                {
                    VungleAdapter.this.log(IronSourceLogger.IronSourceTag.ADAPTER_API, VungleAdapter.this.getProviderName() + ": Cache ad is available for placementId " + placementId, 1);
                    if (VungleAdapter.this.mInitiatedAdUnits.contains("Rewarded Video")) {
                        for (Map.Entry<String, RewardedVideoSmashListener> entry : VungleAdapter.this.mPlacementIdToRewardedVideoSmashListener.entrySet()) {
                            if ((((String)entry.getKey()).equals(placementId)) &&
                                    (entry.getValue() != null)) {
                                ((RewardedVideoSmashListener)entry.getValue()).onRewardedVideoAvailabilityChanged(true);
                            }
                        }
                    }
                    if (VungleAdapter.this.mInitiatedAdUnits.contains("Interstitial")) {
                        for (Map.Entry<String, InterstitialSmashListener> entry : VungleAdapter.this.mPlacementIdToInterstitialSmashListener.entrySet()) {
                            if ((((String)entry.getKey()).equals(placementId)) &&
                                    (entry.getValue() != null)) {
                                ((InterstitialSmashListener)entry.getValue()).onInterstitialAdReady();
                            }
                        }
                    }
                }
            });
        }
    }

    private void loadRewardedVideoAd(String placementReferenceId)
    {
        log(IronSourceLogger.IronSourceTag.ADAPTER_API, getProviderName() + ": loadRewardedVideoAd placementId " + placementReferenceId, 1);

        Vungle.loadAd(placementReferenceId, new LoadAdCallback()
        {
            public void onAdLoad(String placementReferenceId)
            {
                VungleAdapter.this.log(IronSourceLogger.IronSourceTag.ADAPTER_API, VungleAdapter.this.getProviderName() + ": RewardedVideo Ad loaded for placementReferenceId: " + placementReferenceId, 1);
                if (VungleAdapter.this.mPlacementIdToRewardedVideoSmashListener.get(placementReferenceId) != null) {
                    ((RewardedVideoSmashListener)VungleAdapter.this.mPlacementIdToRewardedVideoSmashListener.get(placementReferenceId)).onRewardedVideoAvailabilityChanged(true);
                }
            }

            public void onError(String placementReferenceId, VungleException vungleException)
            {
                VungleAdapter.this.log(IronSourceLogger.IronSourceTag.ADAPTER_API, VungleAdapter.this.getProviderName() + ": RewardedVideo Ad failed to load for placementReferenceId: " + placementReferenceId + ", error: " + vungleException.getLocalizedMessage(), 1);
                if (VungleAdapter.this.mPlacementIdToRewardedVideoSmashListener.get(placementReferenceId) != null) {
                    ((RewardedVideoSmashListener)VungleAdapter.this.mPlacementIdToRewardedVideoSmashListener.get(placementReferenceId)).onRewardedVideoAvailabilityChanged(false);
                }
            }
        });
    }
}
