package com.example;

import com.example.service.GreetingService;
import com.hotreload.annotation.EnableHotReload;

public class DemoApplication {

    public static void main(String[] args) throws Exception {
        GreetingService service = new GreetingService();

        while (true) {
            System.out.println(service.greet());
            Thread.sleep(1000);
        }
    }
}
