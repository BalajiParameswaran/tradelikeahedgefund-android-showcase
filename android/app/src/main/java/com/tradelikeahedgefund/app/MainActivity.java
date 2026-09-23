package com.tradelikeahedgefund.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(SecureStorePlugin.class);
        registerPlugin(OnDeviceAIPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
