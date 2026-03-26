package com.example.demo.controller;

import com.example.demo.config.PlatformConfigProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/location")
@CrossOrigin(origins = "*")
public class LocationController {

    private final RestTemplate restTemplate = new RestTemplate();
    private final PlatformConfigProperties platformConfigProperties;

    public LocationController(PlatformConfigProperties platformConfigProperties) {
        this.platformConfigProperties = platformConfigProperties;
    }

    @GetMapping("/reverse")
    public Map<String, String> reverse(@RequestParam("lat") double lat, @RequestParam("lng") double lng) {
        String fromAmap = reverseByAmap(lat, lng);
        if (fromAmap != null) {
            return Map.of("address", fromAmap);
        }
        String fromNominatim = reverseByNominatim(lat, lng);
        if (fromNominatim != null) {
            return Map.of("address", fromNominatim);
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Reverse geocoding failed");
    }

    private String reverseByAmap(double lat, double lng) {
        String key = platformConfigProperties.getMapApiKey();
        if (key == null || key.isBlank()) {
            return null;
        }
        String url = String.format(
                "https://restapi.amap.com/v3/geocode/regeo?key=%s&location=%f,%f&extensions=base&roadlevel=1",
                key,
                lng,
                lat
        );
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.USER_AGENT, "mk-menswear/1.0");
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                Object status = resp.getBody().get("status");
                if ("1".equals(String.valueOf(status))) {
                    Object regeocode = resp.getBody().get("regeocode");
                    if (regeocode instanceof Map<?, ?> rgc) {
                        Object formatted = rgc.get("formatted_address");
                        if (formatted != null) {
                            return formatted.toString();
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String reverseByNominatim(double lat, double lng) {
        String url = String.format(
                "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=%f&lon=%f&accept-language=zh-CN",
                lat,
                lng
        );
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.USER_AGENT, "mk-menswear/1.0");
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                Object displayName = resp.getBody().get("display_name");
                if (displayName != null) {
                    return displayName.toString();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @GetMapping("/config")
    public Map<String, String> config() {
        return Map.of(
                "mapJsKey", emptyIfNull(platformConfigProperties.getMapJsKey()),
                "mapJsSec", emptyIfNull(platformConfigProperties.getMapJsSec())
        );
    }

    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }
}
