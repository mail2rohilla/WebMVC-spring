package com.example.anotherWebMvc.controllers;

import com.example.anotherWebMvc.pojos.PojoAnother1;
import com.example.anotherWebMvc.pojos.PojoAnother2;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoAnotherController2 {

    @GetMapping(value="rest")
    public PojoAnother1 rest(){
        PojoAnother1 p = new PojoAnother1();
        p.setS1("value1");
        p.setS2(1);
        p.setS3(1000);
        PojoAnother2 p2 = new PojoAnother2();
        p2.setValue(12);
        p.setP2(p2);

        return p;
    }
}
