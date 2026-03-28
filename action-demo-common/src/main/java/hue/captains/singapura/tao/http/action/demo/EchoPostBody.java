package hue.captains.singapura.tao.http.action.demo;

import hue.captains.singapura.tao.http.action.Param;

import java.util.Map;

public record EchoPostBody(Map<String, Object> parsed) implements Param._Post {}
