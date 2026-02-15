package com.example;

import com.example.service.GreetingService;
import com.hotreload.annotation.EnableHotReload;

@EnableHotReload(sourcePaths = {"demo-app/src/main/java"}, pollIntervalMs = 500)
public class DemoApplication {

    public static void main(String[] args) throws Exception {
        GreetingService service = new GreetingService();

        while (true) {
            System.out.println(service.greet());
            Thread.sleep(1000);
        }
    }
}
