package cn.henuer.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class OrderController {

	@GetMapping("one")
	public Map<String,String> one(){
		Map<String,String> result=new HashMap<String, String>();
		result.put("result","hello world");
		return result;
	}
}
