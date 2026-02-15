package com.example.service;

import com.hotreload.annotation.HotReload;

@HotReload
public class GreetingService {
    public String greet() {
       return "Greeting Service V1";
    }
}
