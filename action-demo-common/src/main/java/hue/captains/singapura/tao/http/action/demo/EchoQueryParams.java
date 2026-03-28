package hue.captains.singapura.tao.http.action.demo;

import hue.captains.singapura.tao.http.action.Param;

import java.util.Map;

public record EchoQueryParams(Map<String, String> params) implements Param._QueryString {}
