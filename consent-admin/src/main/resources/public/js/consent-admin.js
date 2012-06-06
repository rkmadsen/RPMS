// Initialize the logout dialog
$(function(){
	PaneManager.initContent($("#content"));
	
	// Register Events	
	// Register Event - Click Select List Item
	PaneManager.on("click", ".selectlistitem", function(event){
		var target = RPMS.findTarget(event, ".selectlistitem");
		var data = RPMS.getObject(target, "data-item");
		PaneManager.cache("selected", data);
		target.parent().children().removeClass("selected");
		target.addClass("selected");
	});
	
	// Register Event - Double Click Select List Item
	PaneManager.on("dblclick", ".selectlistitem", function(event){
		var target = RPMS.findTarget(event, ".selectlistitem");
		var action = RPMS.get(target, "data-action");
		if(action != null){
			var data = RPMS.getObject(target, "data-item");
			PaneManager.cache("selected", data);
			target.parent().children().removeClass("selected");
			target.addClass("selected");
			RPMS.doAction(action);
		}
	});

	// Register Event - Click Generic Push Action
	PaneManager.on("click", ".push-action", function(event){
		var target = RPMS.findTarget(event, "div.push-action");
		var url = RPMS.get(target, "data-url");
		var params = RPMS.getParamMap(target, "data-map");
		var confirm = RPMS.getObject(target, "data-confirm");
		var action = function(){
			Dialog.Progress.start();
			PaneManager.push(url, params, {postpop: function(o,p,c){c.refresh()}});
			}
		
		if(confirm != null){
			Dialog.confirm({title: confirm.title, 
				message: confirm.message, 
				onconfirm: action,
				oncancel: Dialog.Progress.end});
		}
		else{
			action();
		}
	});

	// Register Event - Click Generic Push Action
	PaneManager.on("click", ".ajax-action", function(event){
		var target = RPMS.findTarget(event, "div.ajax-action");
		
		var method = RPMS.get(target, "data-method");
		var url = RPMS.get(target, "data-url");
		var params = RPMS.getParamMap(target, "data-map");
		var fullUrl = Utils.Url.render(url, params);
		
		var confirm = RPMS.getObject(target, "data-confirm");
		var includeData = RPMS.getBoolean(target, "data-include-data");
		var actionOnSuccess = RPMS.get(target, "data-action-on-success");
		
		var settings = {
				data: includeData ? RPMS.getDataMap() : null ,
				type: method,
				dataType: "text",
				success: function(data, status, xhr){
					Dialog.Progress.end();
					if(actionOnSuccess != null){
						RPMS.doAction(actionOnSuccess);
					}
				},
				error: function(xhr, status, text){
					Dialog.Progress.end();
					var message = RPMS.responseMessage(xhr, "Failed to save data.");
					Dialog.inform({title: "Error Encountered", message: message});
				}
		}
		
		var action = function(){
			Dialog.Progress.start();
			$.ajax(fullUrl, settings);
		}
		
		if(confirm != null){
			Dialog.confirm({title: confirm.title, 
				message: confirm.message, 
				onconfirm: action});
		}
		else{
			action();
		}
	});
	
	// Register Event - Click Back Button
	PaneManager.on("click", ".back-action", function(event){
		var target = RPMS.findTarget(event, "div.back-action");
		var params = RPMS.getParamMap(target, "data-map");
		PaneManager.pop(params);
	});
});

var RPMS = {
	findTarget: function(event, selector){
		var element = $(event.target);
		if(element.filter(selector).length > 0){
			return element.first();
		}
		else{
			return element.parents(selector).first();
		}
	},
	getObject: function(element, attribute){
		var value = RPMS.get(element, attribute);
		if(value != null && typeof value == 'string'){
			return $.parseJSON(value);
		}
		return value;
	},
	
	get: function(target, attribute, defaultValue){
		if(attribute.indexOf("data-") != 0){
			attribute = "data-" + attribute;
		}
		var value = null;
		if(target.getAttribute){
			value = target.getAttribute(attribute);
		}
		else if(target.attr){
			value = target.attr(attribute);
		} 
		if(value == null){
			return defaultValue;
		}
		return value;
	},
	
	getBoolean: function(target, attribute, defaultValue){
		if(attribute.indexOf("data-") != 0){
			attribute = "data-" + attribute;
		}
		var val = RPMS.get(target, attribute, defaultValue);
		if(val != null && (val == true || val == "true" || val == attribute)){
			return true;
		}
		return false;
	},
	
	mapToParams: function(map){
		var params = {};
		if(map){
			for(var key in map){
				var val = PaneManager.cache(map[key]);
				if(val != null){
					params[key] = val;
				}
				else{
					params[key] = map[key];
				}
			}
		}
		return params;
	},
	getParamMap: function(element, attr){
		var map = RPMS.getObject(element, attr);
		if(map){
			return RPMS.mapToParams(map);
		}
		return PaneManager.cache();
	},
	getForm: function(){
		return PaneManager.current.pane.find("form").first();
	},
	getDataMap: function(changes){
		var data = {};
		var form = RPMS.getForm();
		var inputs = form.find(":input");
		inputs.each(function(i, e){
			var name = e.name;
			var origValue = RPMS.get(e, "data-value");
			var value = null;
			if(e.tagName.toLowerCase() == "input" && e.type.toLowerCase() == "checkbox"){
				var checkVal = RPMS.get(e, "data-checked-value", true);
				var uncheckVal = RPMS.get(e, "data-unchecked-value", false);
				if(e.checked){
					value = checkVal;
				}
				else{
					value = uncheckVal;
				}
			}
			else{
				value = $(e).val();
			}
			if(changes != true || value != origValue){
				data[name] = value;
			}
		});
		return data;
	},
	
	responseMessage: function(xhr, ifnull){
		if(xhr && xhr.responseText){
			var body = $.parseJSON(xhr.responseText);
			if(body && body.message){
				return body.message;
			}
		}
		return ifnull;
	},
	doAction: function(action){
		if(PaneManager[action] != null){
			PaneManager[action]();
			return;
		}
		
		var buttons = PaneManager.current.pane.find(action);
		if(buttons.length > 0){
			buttons.trigger("click");
			return;
		}
	}
};