package com.example.webmvc.service;

import org.springframework.stereotype.Service;

@Service
public class NameFindingService {
    static int i = 0;
    public static String getName(){
        return (i++) + "th name";
    }
}
