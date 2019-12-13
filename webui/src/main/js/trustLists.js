class TrustList {
	constructor(xmlNode) {
		this.user = xmlNode.getElementsByTagName("User")[0].childNodes[0].nodeValue
		this.userB64 = xmlNode.getElementsByTagName("UserB64")[0].childNodes[0].nodeValue
		this.status = xmlNode.getElementsByTagName("Status")[0].childNodes[0].nodeValue
		this.timestamp = xmlNode.getElementsByTagName("Timestamp")[0].childNodes[0].nodeValue
		this.trusted = xmlNode.getElementsByTagName("Trusted")[0].childNodes[0].nodeValue
		this.distrusted = xmlNode.getElementsByTagName("Distrusted")[0].childNodes[0].nodeValue
	}
	
	getMapping() {
		var mapping = new Map()
		
		var userLink = new Link(this.user, "displayList", [this.user])
		var unsubscribeLink = new Link(_t("Unsubscribe"), "unsubscribe", [this.userB64])
		var refreshLink = new Link(_t("Refresh"), "forceUpdate", [this.userB64])
		
		mapping.set("Name", userLink.render() + unsubscribeLink.render() + refreshLink.render())
		mapping.set("Status", this.status)
		mapping.set("Last Updated", this.timestamp)
		mapping.set("Trusted", this.trusted)
		mapping.set("Distrusted", this.distrusted)
		
		return mapping
	}
}

class Persona {
	constructor(xmlNode) {
		this.user = xmlNode.getElementsByTagName("User")[0].childNodes[0].nodeValue
		this.userB64 = xmlNode.getElementsByTagName("UserB64")[0].childNodes[0].nodeValue
		try {
			this.reason = xmlNode.getElementsByTagName("Reason")[0].childNodes[0].nodeValue
		} catch (ignore) {
			this.reason = ""
		}
		this.status = xmlNode.getElementsByTagName("Status")[0].childNodes[0].nodeValue
	}
	
	getTrustBlock() {
		return "<span id='trusted-link-" + this.userB64 + "'>" + this.getTrustLink() + "</span>" +
				"<span id='trusted-" + this.userB64 + "'></span>"
	}
	
	getDistrustBlock() {
		return "<span id='distrusted-link-" + this.userB64 + "'>" + this.getDistrustLink() + "</span>" +
				"<span id='distrusted-" + this.userB64 + "'></span>"
	}
	
	getTrustLink() {
		return "<a href='#' onclick='markTrusted(\"" + this.userB64 + "\");return false;'>" + _t("Mark Trusted") + "</a>"
	}
	
	getNeutralLink() {
		return "<a href='#' onclick='markNeutral(\"" + this.userB64 + "\");return false;'>" + _t("Mark Neutral") + "</a>"
	}
	
	getDistrustLink() {
		return "<a href='#' onclick='markDistrusted(\"" + this.userB64 + "\");return false;'>" + _t("Mark Distrusted") + "</a>"
	}
	
	getTrustActions() {
		if (this.status == "TRUSTED")
			return [this.getNeutralLink(), this.getDistrustBlock()]
		if (this.status == "NEUTRAL")
			return [this.getTrustBlock(), this.getDistrustBlock()]
		if (this.status == "DISTRUSTED")
			return [this.getTrustBlock(), this.getNeutralLink()]
		return null
	}
}

var lists = new Map()
var revision = -1
var currentUser = null

var listsSortKey
var listsSortOrder

function markTrusted(user) {
	var linkSpan = document.getElementById("trusted-link-" + user)
	linkSpan.innerHTML = ""
	
	var textAreaSpan = document.getElementById("trusted-" + user)
	
	var textbox = "<textarea id='trust-reason-" + user + "'></textarea>"
	var submitLink = "<a href='#' onclick='window.submitTrust(\"" + user + "\");return false;'>" + _t("Submit") + "</a>"
	var cancelLink = "<a href='#' onclick='window.cancelTrust(\"" + user + "\");return false;'>" + _t("Cancel") + "</a>"
	
	var html = "<br/>" + _t("Enter Reason (Optional)") + "<br/>" + textbox + "<br/>" + submitLink + " " + cancelLink + "<br/>"
	
	textAreaSpan.innerHTML = html
}

function submitTrust(user) {
	var reason = document.getElementById("trust-reason-" + user).value
	publishTrust(user, reason, "trust")
}

function cancelTrust(user) {
	var textAreaSpan = document.getElementById("trusted-" + user)
	textAreaSpan.innerHTML = ""
	
	var linkSpan = document.getElementById("trusted-link-" + user)
	var html = "<a href='#' onclick='markTrusted(\"" + user + "\");return false;'>" + _t("Mark Trusted") + "</a>"
	linkSpan.innerHTML = html
}

function markNeutral(user) {
	publishTrust(user, "", "neutral")
}

function markDistrusted(user) {
	var linkSpan = document.getElementById("distrusted-link-" + user)
	linkSpan.innerHTML = ""
	
	var textAreaSpan = document.getElementById("distrusted-" + user)
	
	var textbox = "<textarea id='distrust-reason-" + user + "'></textarea>"
	var submitLink = "<a href='#' onclick='window.submitDistrust(\"" + user + "\");return false;'>" + _t("Submit") + "</a>"
	var cancelLink = "<a href='#' onclick='window.cancelDistrust(\"" + user + "\");return false;'>" + _t("Cancel") + "</a>"
	
	var html = "<br/>" + _t("Enter Reason (Optional)") + "<br/>" + textbox + "<br/>" + submitLink + " " + cancelLink + "<br/>"
	
	textAreaSpan.innerHTML = html
}

function submitDistrust(user) {
	var reason = document.getElementById("distrust-reason-" + user).value
	publishTrust(user, reason, "distrust")
}

function cancelDistrust(user) {
	var textAreaSpan = document.getElementById("distrusted-" + user)
	textAreaSpan.innerHTML = ""
	
	var linkSpan = document.getElementById("distrusted-link-" + user)
	var html = "<a href='#' onclick='markDistrusted(\"" + user + "\");return false;'>" + _t("Mark Distrusted") + "</a>"
	linkSpan.innerHTML = html
}

function publishTrust(host, reason, trust) {
	var xmlhttp = new XMLHttpRequest()
	xmlhttp.onreadystatechange = function() {
		if (this.readyState == 4 && this.status == 200) {
			refreshLists()
		}
	}
	xmlhttp.open("POST","/MuWire/Trust", true)
	xmlhttp.setRequestHeader('Content-type', 'application/x-www-form-urlencoded');
	xmlhttp.send("action=" + trust + "&reason=" + reason + "&persona=" + host)
}

function unsubscribe(user) {
	var xmlhttp = new XMLHttpRequest()
	xmlhttp.onreadystatechange = function() {
		if (this.readyState == 4 && this.status == 200) {
			refreshLists()
		}
	}
	xmlhttp.open("POST","/MuWire/Trust", true)
	xmlhttp.setRequestHeader('Content-type', 'application/x-www-form-urlencoded');
	xmlhttp.send("action=unsubscribe&persona=" + user)
}

function forceUpdate(user) {
	var xmlhttp = new XMLHttpRequest()
	xmlhttp.onreadystatechange = function() {
		if (this.readyState == 4 && this.status == 200) {
			refreshLists()
		}
	}
	xmlhttp.open("POST","/MuWire/Trust", true)
	xmlhttp.setRequestHeader('Content-type', 'application/x-www-form-urlencoded');
	xmlhttp.send("action=subscribe&persona=" + user)
}

function updateDiv(name, list) {
	
	var html = "<table><thead><tr><th>" + _t("User") + "</th><th>" + _t("Reason") + "</th><th>" + _t("Your Trust") + "</th><th>" + _t("Actions") + "</th></tr></thead><tbody>"
	
	var i
	for (i = 0; i < list.length; i++) {
		html += "<tr>"
		html += "<td>" + list[i].user + "</td>"
		html += "<td>" + list[i].reason + "</td>"  // maybe in <pre>
		html += "<td>" + list[i].status + "</td>"
		html += "<td>" + list[i].getTrustActions().join(" ") + "</td>"
		html += "</tr>"
	}
	
	document.getElementById(name).innerHTML = html
}

function parse(xmlNode, list) {
	var users = xmlNode.getElementsByTagName("Persona")
	var i
	for (i = 0; i < users.length; i++)
		list.push(new Persona(users[i]))
}

function displayList(user) {
	currentUser = user
	
	var currentList = lists.get(currentUser)
	
	var xmlhttp = new XMLHttpRequest()
	xmlhttp.onreadystatechange = function() {
		if (this.readyState == 4 && this.status == 200) {
			var trusted = []
			var distrusted = []
			
			var xmlNode = this.responseXML.getElementsByTagName("Trusted")[0]
			parse(xmlNode, trusted)
			xmlNode = this.responseXML.getElementsByTagName("Distrusted")[0]
			parse(xmlNode, distrusted)
			
			var currentListDiv = document.getElementById("currentList")
			currentListDiv.innerHTML = "Trust List Of " + user
		
			updateDiv("trusted", trusted)
			updateDiv("distrusted", distrusted)	
		}
	}
	xmlhttp.open("GET", "/MuWire/Trust?section=list&user=" + currentList.userB64)
	xmlhttp.send()
}

function sortSubscriptions(key, order) {
	listsSortKey = key
	listsSortOrder = order
	refreshLists()
}

function refreshLists() {
	var xmlhttp = new XMLHttpRequest()
	xmlhttp.onreadystatechange = function() {
		if (this.readyState == 4 && this.status == 200) {
			lists.clear()
			var listOfLists = []
			var subs = this.responseXML.getElementsByTagName("Subscription")
			var i
			for (i = 0; i < subs.length; i++) {
				var trustList = new TrustList(subs[i])
				lists.set(trustList.user, trustList)
				listOfLists.push(trustList)				
			}
			
			var newOrder
			if (listsSortOrder == "descending")
				newOrder = "ascending"
			else if (listsSortOrder == "ascending")
				newOrder = "descending"
			var table = new Table(["Name","Trusted","Distrusted","Status","Last Updated"], "sortSubscriptions", listsSortKey, newOrder)
			
			for (i = 0; i < listOfLists.length; i++) {
				table.addRow(listOfLists[i].getMapping())
			}
			
			document.getElementById("trustLists").innerHTML = table.render()
			
			if (currentUser != null)
				displayList(currentUser)
		}
	}
	var sortParam = "&key=" + listsSortKey + "&order=" + listsSortOrder
	xmlhttp.open("GET", encodeURI("/MuWire/Trust?section=subscriptions" + sortParam), true)
	xmlhttp.send()
}

function fetchRevision() {
	var xmlhttp = new XMLHttpRequest()
	xmlhttp.onreadystatechange = function() {
		if (this.readyState == 4 && this.status == 200) {
			var xmlDoc = this.responseXML
			var newRevision = xmlDoc.childNodes[0].childNodes[0].nodeValue
			if (newRevision > revision) {
				revision = newRevision
				refreshLists()
			}
		}
	}
	xmlhttp.open("GET", "/MuWire/Trust?section=revision", true)
	xmlhttp.send()
}

function initTrustLists() {
	setTimeout(fetchRevision, 1)
	setInterval(fetchRevision, 3000)
}
