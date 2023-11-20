package com.paytm.acquirer.netc.dto.common;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VehicleDetailsXml {
    @JacksonXmlProperty(localName = "Detail")
    @JacksonXmlElementWrapper(useWrapping = false)
    private Set<Detail> details;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Detail {
        @JacksonXmlProperty(isAttribute = true)
        private String name;
        @JacksonXmlProperty(isAttribute = true)
        private String value;

        @Override
        public boolean equals(Object other) {
            if (other == null) {
                return false;
            }
            if (!(other instanceof Detail)) {
                return false;
            } else {
                Detail otherDetail = (Detail) other;
                return otherDetail.name == this.name;
            }
        }
    }
}
