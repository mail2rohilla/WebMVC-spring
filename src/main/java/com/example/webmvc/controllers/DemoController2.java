package com.example.webmvc.controllers;

import com.example.webmvc.controllers.pojos.Pojo1;
import com.example.webmvc.controllers.pojos.Pojo2;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
public class DemoController2 {

    @GetMapping(value="rest")
    public Pojo1 rest(){
        Pojo1 p = new Pojo1();
        p.setS1("value1");
        p.setS2(1);
        p.setS3(1000);
        Pojo2 p2 = new Pojo2();
        p2.setValue(12);
        p.setP2(p2);

        return p;
    }
}
