package com.paytm.acquirer.netc.controller;

import com.paytm.acquirer.netc.util.Constants;
import com.paytm.transport.service.DynamicPropertyService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = Constants.BASE_URL)
@Hidden
public class DynamicPropertyController {

  @Value("${dynamic.prop.app.name}")
  private String appName;
  private final DynamicPropertyService dynamicPropertyService;

  @PostMapping("add_dynamic_key")
  public String addDynamicKey(@RequestParam(value = "appName") String appName,
                               @RequestParam("key") String key,
                               @RequestParam("value") String value) {
    if (StringUtils.isEmpty(appName)) appName = this.appName;
    return dynamicPropertyService.addDynamicTypedProperty(appName, key, value).toString();
  }

  @PutMapping("update_dynamic_key")
  public String updateDynamicKey(@RequestParam("id") Long id,
                                  @RequestParam("value") String value) {
    return dynamicPropertyService.updateDynamicTypedProperty(id, value).toString();
  }


}
