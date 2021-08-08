package com.example.webmvc.controllers;

import com.example.webmvc.service.NameFindingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@RequestMapping(path="/temp")
public class DemoController {

    @Autowired
    NameFindingService nameFindingService;

    @RequestMapping( method = RequestMethod.GET)
    public String display(ModelMap model)
    {
        System.out.println("entered here");

        model.addAttribute("name", nameFindingService.getName());
        return "index";
    }
}
