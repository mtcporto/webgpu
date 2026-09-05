package com.example.gemmawebgpu;

import com.getcapacitor.BridgeActivity;
import com.getcapacitor.Plugin;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(android.os.Bundle savedInstanceState) {
        registerPlugin(NativeGemmaPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
