package com.prashant.codegen.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UrlShortenerSpec {
    private String baseUrl;
    private int codeLength;
    private int expiryDays;
    private boolean analytics;
}


