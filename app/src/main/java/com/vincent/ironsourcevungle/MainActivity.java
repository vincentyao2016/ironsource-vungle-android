package com.vincent.ironsourcevungle;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.ironsource.mediationsdk.IronSource;
import com.ironsource.mediationsdk.logger.IronSourceError;
import com.ironsource.mediationsdk.model.Placement;
import com.ironsource.mediationsdk.sdk.InterstitialListener;
import com.ironsource.mediationsdk.sdk.RewardedVideoListener;

public class MainActivity extends AppCompatActivity implements View.OnClickListener  {


    private String appKey,interstitialPlacementId,rewardPlacementId;
    private Context context;
    private static String TAG = "IronSource-Vungle";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = MainActivity.this;
        initView();
    }

    public void initView() {
        Button initBtn = findViewById(R.id.init_btn);
        Button loadInterstitialBtn = findViewById(R.id.load_interstitial_btn);
        Button playInterstitialBtn = findViewById(R.id.play_interstitial_btn);
        Button playRewardBtn = findViewById(R.id.play_reward_btn);

        initBtn.setOnClickListener(this);
        loadInterstitialBtn.setOnClickListener(this);
        playInterstitialBtn.setOnClickListener(this);
        playRewardBtn.setOnClickListener(this);

        appKey = context.getString(R.string.app_id);
        interstitialPlacementId = context.getString(R.string.interstitial_placement_id);
        rewardPlacementId = context.getString(R.string.reward_placement_id);
    }

    private void initMediation() {
        IronSource.init(this, appKey, IronSource.AD_UNIT.INTERSTITIAL, IronSource.AD_UNIT.REWARDED_VIDEO);
        IronSource.setRewardedVideoListener(new RewardedVideoListener() {

            @Override
            public void onRewardedVideoAdOpened() {
                Log.d(TAG,"onRewardedVideoAdOpened");
            }

            @Override
            public void onRewardedVideoAdClosed() {
                Log.d(TAG,"onRewardedVideoAdClosed");
            }

            @Override
            public void onRewardedVideoAvailabilityChanged(boolean available) {
                Log.d(TAG,"onRewardedVideoAvailabilityChanged" + available);
            }

            @Override
            public void onRewardedVideoAdRewarded(Placement placement) {
                Log.d(TAG,"onRewardedVideoAdRewarded" + placement.getPlacementName());
            }

            @Override
            public void onRewardedVideoAdShowFailed(IronSourceError error) {
                Log.d(TAG,"onRewardedVideoAdShowFailed" + error.getErrorMessage());
            }

            @Override
            public void onRewardedVideoAdClicked(Placement placement){
                Log.d(TAG,"onRewardedVideoAdClicked" + placement.getPlacementName());
            }
            @Override
            public void onRewardedVideoAdStarted(){
                Log.d(TAG,"onRewardedVideoAdStarted");
            }

            @Override
            public void onRewardedVideoAdEnded(){
                Log.d(TAG,"onRewardedVideoAdEnded");
            }
        });

        IronSource.setInterstitialListener(new InterstitialListener() {

            @Override
            public void onInterstitialAdReady() {
                Log.d(TAG,"onInterstitialAdReady");
            }

            @Override
            public void onInterstitialAdLoadFailed(IronSourceError error) {
                Log.d(TAG,"onInterstitialAdLoadFailed" + error.getErrorMessage());
            }

            @Override
            public void onInterstitialAdOpened() {
                Log.d(TAG,"onInterstitialAdOpened");
            }

            @Override
            public void onInterstitialAdClosed() {
                Log.d(TAG,"onInterstitialAdClosed");
            }

            @Override
            public void onInterstitialAdShowSucceeded() {
                Log.d(TAG,"onInterstitialAdShowSucceeded");
            }

            @Override
            public void onInterstitialAdShowFailed(IronSourceError error) {
                Log.d(TAG,"onInterstitialAdShowFailed" + error.getErrorMessage());
            }

            @Override
            public void onInterstitialAdClicked() {
                Log.d(TAG,"onInterstitialAdClicked");
            }
        });
    }

    private void loadInterstitial() {
        IronSource.loadInterstitial();
    }

    private void playInterstitial() {
        IronSource.showInterstitial(interstitialPlacementId);
    }

    private void playReward() {
        boolean available = IronSource.isRewardedVideoAvailable();
        if(available) {
            IronSource.showRewardedVideo(rewardPlacementId);
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.init_btn:
                initMediation();
                break;
            case R.id.load_interstitial_btn:
                loadInterstitial();
                break;
            case R.id.play_interstitial_btn:
                playInterstitial();
            case R.id.play_reward_btn:
                playReward();
                break;
        }
    }

    protected void onResume() {
        super.onResume();
        IronSource.onResume(this);
    }
    protected void onPause() {
        super.onPause();
        IronSource.onPause(this);
    }
}
