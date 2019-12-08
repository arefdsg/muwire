class SearchStatus {
	constructor(xmlNode) {
		this.revision = xmlNode.getElementsByTagName("Revision")[0].childNodes[0].nodeValue
		this.query = xmlNode.getElementsByTagName("Query")[0].childNodes[0].nodeValue
		this.uuid = xmlNode.getElementsByTagName("uuid")[0].childNodes[0].nodeValue
		this.senders = xmlNode.getElementsByTagName("Senders")[0].childNodes[0].nodeValue
		this.results = xmlNode.getElementsByTagName("Results")[0].childNodes[0].nodeValue
	}
}

class SearchBySender {
	constructor(xmlNode) {
		this.resultBatches = new Map();
		
		var resultsBySender = xmlNode.getElementsByTagName("ResultsBySender")[0];
		var resultsFromSenders = resultsBySender.getElementsByTagName("ResultsFromSender");
		var i;
		for (i = 0; i < resultsFromSenders.length; i++) {
			var results = new ResultsBySender(resultsFromSenders[i]);
			this.resultBatches.set(results.sender, results);
		}
	}
}

class SearchByFile {
	constructor(xmlNode) {
		this.resultBatches = new Map();
		
		var resultsByFile = xmlNode.getElementsByTagName("ResultsByFile")[0];
		var resultsForFile = resultsByFile.getElementsByTagName("ResultsForFile");
		var i;
		for (i = 0; i < resultsForFile.length; i++) {
			var results = new ResultsByFile(resultsForFile[i]);
			this.resultBatches.set(results.infoHash, results);
		}
	}
}

class ResultsBySender {
	constructor(xmlNode) {
		this.sender = xmlNode.getElementsByTagName("Sender")[0].childNodes[0].nodeValue;
		this.senderB64 = xmlNode.getElementsByTagName("SenderB64")[0].childNodes[0].nodeValue;
		this.browse = xmlNode.getElementsByTagName("Browse")[0].childNodes[0].nodeValue;
		this.browsing = xmlNode.getElementsByTagName("Browsing")[0].childNodes[0].nodeValue;
		this.results = new Map();
		var resultNodes = xmlNode.getElementsByTagName("Result");
		var i;
		for (i = 0 ; i < resultNodes.length; i ++) {
			var result = new ResultBySender(resultNodes[i]);
			this.results.set(result.infoHash,result);
		}
	}
}

class ResultsByFile {
	constructor(xmlNode) {
		this.name = xmlNode.getElementsByTagName("Name")[0].childNodes[0].nodeValue;
		this.infoHash = xmlNode.getElementsByTagName("InfoHash")[0].childNodes[0].nodeValue;
		this.size = xmlNode.getElementsByTagName("Size")[0].childNodes[0].nodeValue;
		this.downloading = xmlNode.getElementsByTagName("Downloading")[0].childNodes[0].nodeValue;
		this.results = new Map();
		var resultNodes = xmlNode.getElementsByTagName("Result");
		var i;
		for (i = 0; i < resultNodes.length; i++) {
			var result = new ResultByFile(resultNodes[i]);
			this.results.set(result.sender, result);
		}
	}
}

class ResultBySender {
	constructor(xmlNode) {
		this.name = xmlNode.getElementsByTagName("Name")[0].childNodes[0].nodeValue;
		this.size = xmlNode.getElementsByTagName("Size")[0].childNodes[0].nodeValue;
		this.infoHash = xmlNode.getElementsByTagName("InfoHash")[0].childNodes[0].nodeValue;
		this.downloading = xmlNode.getElementsByTagName("Downloading")[0].childNodes[0].nodeValue;
		this.comment = null;
		var comment = xmlNode.getElementsByTagName("Comment")
		if (comment.length == 1) 
			this.comment = comment[0].childNodes[0].nodeValue;
	}
}

class ResultByFile {
	constructor(xmlNode) {
		this.sender = xmlNode.getElementsByTagName("Sender")[0].childNodes[0].nodeValue;
		this.senderB64 = xmlNode.getElementsByTagName("SenderB64")[0].childNodes[0].nodeValue;
		this.browse = xmlNode.getElementsByTagName("Browse")[0].childNodes[0].nodeValue;
		this.browsing = xmlNode.getElementsByTagName("Browsing")[0].childNodes[0].nodeValue;
		this.comment = null;
		var comment = xmlNode.getElementsByTagName("Comment")
		if (comment.length == 1) 
			this.comment = comment[0].childNodes[0].nodeValue;
	}
}

var statusByUUID = new Map()
var currentSearchBySender = null
var currentSearchByFile = null
var expandedComments = new Map();

var uuid = null;
var sender = null;
var lastXML = null;
var infoHash = null;

function showCommentBySender(divId, spanId) {
	var split = divId.split("_");
	var commentDiv = document.getElementById(divId);
	var comment = "<pre>"+ currentSearchBySender.resultBatches.get(split[2]).results.get(split[3]).comment + "</pre>";
	commentDiv.innerHTML = comment
	expandedComments.set(divId, comment);
	var hideLink = "<a href='#' onclick='window.hideComment(\""+divId+"\",\""+spanId+"\",\"Sender\");return false;'>Hide Comment</a>";
    var linkSpan = document.getElementById(spanId);
	linkSpan.innerHTML = hideLink;
}

function showCommentByFile(divId, spanId) {
	var split = divId.split("_");
	var commentDiv = document.getElementById(divId);
	var comment = "<pre>"+currentSearchByFile.resultBatches.get(split[2]).results.get(split[3]).comment + "</pre>";
	commentDiv.innerHTML = comment
	expandedComments.set(divId, comment);
	var hideLink = "<a href='#' onclick='window.hideComment(\""+divId+"\",\""+spanId+"\",\"File\");return false;'>Hide Comment</a>";
    var linkSpan = document.getElementById(spanId);
	linkSpan.innerHTML = hideLink;
}

function hideComment(divId, spanId, byFile) {
	expandedComments.delete(divId);
	var commentDiv = document.getElementById(divId);
	commentDiv.innerHTML = ""
	var showLink = "<a href='#' onclick='window.showCommentBy"+byFile+"(\"" + divId + "\",\"" + spanId + "\"); return false;'>Show Comment</a>";
	var linkSpan = document.getElementById(spanId);
	linkSpan.innerHTML = showLink;
}

function download(resultInfoHash) {
	var xmlhttp = new XMLHttpRequest();
	xmlhttp.onreadystatechange = function() {
		if (this.readyState == 4 && this.status == 200) {
			var resultSpan = document.getElementById("download-"+resultInfoHash);
			resultSpan.innerHTML = "Downloading";
		}
	}
	xmlhttp.open("POST", "/MuWire/Download", true);
	xmlhttp.setRequestHeader('Content-type', 'application/x-www-form-urlencoded');
	xmlhttp.send(encodeURI("action=start&infoHash="+resultInfoHash+"&uuid="+uuid));
}

function updateSender(senderName) {
	sender = senderName;
	
	var resultsFromSpan = document.getElementById("resultsFrom");
	resultsFromSpan.innerHTML = "Results From "+sender;
	
	var resultsDiv = document.getElementById("bottomTable");
	var table = "<table><thead><tr><th>Name</th><th>Size</th><th>Download</th></tr></thead><tbody>"
	var x = currentSearchBySender
	x = x.resultBatches.get(sender).results;
	for (var [resultInfoHash, result] of x) {
		table += "<tr>";
		table += "<td>";
		table += result.name;
		if (result.comment != null) {
			var divId = "comment_" + uuid + "_" + senderName + "_" + resultInfoHash;
			var spanId = "comment-link-"+resultInfoHash + senderName + uuid;
			var comment = expandedComments.get(divId);
			if (comment != null) {
				var link = "<a href='#' onclick='window.hideComment(\""+divId +"\",\"" + spanId + "\",\"Sender\");return false;'>Hide Comment</a>";
				table += "<br/><span id='"+spanId+"'>" + link + "</span><br/>";
				table += "<div id='" + divId + "'>"+comment+"</div>";				
			} else {
				var link = "<a href='#' onclick='window.showCommentBySender(\"" + divId +
					"\",\""+spanId+"\");"+
					"return false;'>Show Comment</a>"; 			
				table += "<br/><span id='"+spanId+"'>"+link+"</span>";
				table += "<div id='"+divId+"'></div>";
			}
		}
		table += "</td>";
		table += "<td>";
		table += result.size;
		table += "</td>";
		table += "<td>";
		if (result.downloading == "false") {
			table += "<span id='download-"+ resultInfoHash+"'><a href='#' onclick='window.download(\"" + resultInfoHash + "\");return false;'>Download</a></span>";
		} else {
			table += "Downloading";
		}
		table += "</td>";
		table += "</tr>";
	}
	table += "</tbody></table>";
	if (x.size > 0)
		resultsDiv.innerHTML = table;
}

function updateFile(fileInfoHash) {
	infoHash = fileInfoHash;
	
	var searchResults = currentSearchByFile.resultBatches.get(infoHash);
	
	var resultsFromSpan = document.getElementById("resultsFrom");
	resultsFromSpan.innerHTML = "Results For "+searchResults.name;
	
	var resultsDiv = document.getElementById("bottomTable");
	var table = "<table><thead><tr><th>Sender</th><th>Browse</th></tr></thead><tbody>";
	var i;
	for (var [senderName, result] of searchResults.results) {
		table += "<tr>";
		table += "<td>";
		table += senderName
		if (result.comment != null) {
			var divId = "comment_" + uuid + "_" + fileInfoHash + "_" + senderName;
			var spanId = "comment-link-" + fileInfoHash + senderName + uuid;
			var comment = expandedComments.get(divId);
			if (comment != null) {
				var link = "<a href='#' onclick='window.hideComment(\""+divId +"\",\"" + spanId + "\",\"File\");return false;'>Hide Comment</a>";
				table += "<br/><span id='"+spanId+"'>" + link + "</span><br/>";
				table += "<div id='" + divId + "'>"+comment+"</div>";
			} else {
				var link = "<a href='#' onclick='window.showCommentByFile(\"" + divId +
					"\",\""+spanId+"\");"+
					"return false;'>Show Comment</a>"; 			
				table += "<br/><span id='"+spanId+"'>"+link+"</span>";
				table += "<div id='"+divId+"'></div>";
			}
		}
		table += "</td>";
		if (result.browse == "true") {
			if (result.browsing == "true")
				table += "<td>Browsing</td>"
			else {
				table += "<td><span id='browse-link-" + result.senderB64 + "'>" + getBrowseLink(result.senderB64) + "</span></td>"
			}
		}
		table += "</tr>";
	}
	table += "</tbody></table>";
	if (searchResults.results.size > 0)
		resultsDiv.innerHTML = table;
}			

function updateUUIDBySender(resultUUID) {
	uuid = resultUUID;
	
	var currentStatus = statusByUUID.get(uuid)
	
	var currentSearchSpan = document.getElementById("currentSearch");
	currentSearchSpan.innerHTML = currentStatus.query + " Results";
	
	var sendersDiv = document.getElementById("topTable");
	var table = "<table><thead><tr><th>Sender</th><th>Browse</th></tr></thead><tbody>";
	var x = currentSearchBySender.resultBatches;
	for (var [senderName, senderBatch] of x) {
		table += "<tr><td><a href='#' onclick='updateSender(\""+senderName+"\");return false;'>"
		table += senderName;
		table += "</a></td>";
		if (senderBatch.browse == "true") {
			if (senderBatch.browsing == "true") 
				table += "<td>Browsing</td>"
			else 
				table += "<td><span id='browse-link-" + senderBatch.senderB64 + "'>" + getBrowseLink(senderBatch.senderB64) + "</span></td>"
		} 
		table += "</tr>";
	}
	table += "</tbody></table>";
	if (x.size > 0)
		sendersDiv.innerHTML = table;
	if (sender != null)
		updateSender(sender);
}

function updateUUIDByFile(resultUUID) {
	uuid = resultUUID;
	
	var currentStatus = statusByUUID.get(uuid)
	
	var currentSearchSpan = document.getElementById("currentSearch");
	currentSearchSpan.innerHTML = currentStatus.query + " Results";
	
	var topTableDiv = document.getElementById("topTable");
	var table = "<table><thead><tr><th>Name</th><th>Size</th><th>Download</th></tr></thead><tbody>";
	var x = currentSearchByFile.resultBatches;
	for (var [fileInfoHash, file] of x) {
		table += "<tr><td><a href='#' onclick='updateFile(\""+fileInfoHash+"\");return false;'>";
		table += file.name;
		table += "</a></td>";
		table += "<td>";
		table += file.size;
		table += "</td>";
		table += "<td>";
		if (file.downloading == "false") 
			table += "<span id='download-"+fileInfoHash+"'><a href='#' onclick='window.download(\""+fileInfoHash+"\"); return false;'>Download</a></span>";
		else
			table += "Downloading";
		table += "</td></tr>";
	}
	table += "</tbody></table>";
	if (x.size > 0) 
		topTableDiv.innerHTML = table;
	if (infoHash != null)
		updateFile(infoHash);
}

function refreshGroupBySender(searchUUID) {
	var xmlhttp = new XMLHttpRequest();
	xmlhttp.onreadystatechange = function () {
		if (this.readyState == 4 && this.status == 200) {
			var xmlDoc = this.responseXML;
			currentSearchBySender = new SearchBySender(xmlDoc)
			updateUUIDBySender(searchUUID);
		}
	}
	xmlhttp.open("GET", "/MuWire/Search?section=groupBySender&uuid="+searchUUID, true);
	xmlhttp.send();
}

function refreshGroupByFile(searchUUID) {
	var xmlhttp = new XMLHttpRequest();
	xmlhttp.onreadystatechange = function () {
		if (this.readyState == 4 && this.status == 200) {
			var xmlDoc = this.responseXML;
			
			currentSearchByFile = new SearchByFile(xmlDoc)
			updateUUIDByFile(searchUUID)
		}
	}
	xmlhttp.open("GET", "/MuWire/Search?section=groupByFile&uuid="+searchUUID, true);
	xmlhttp.send();
}

function getBrowseLink(host) {
	return "<a href='#' onclick='window.browse(\"" + host + "\"); return false;'>Browse</a>"
}

function browse(host) {
	var xmlhttp = new XMLHttpRequest()
	xmlhttp.onreadystatechange = function() {
		if (this.readyState == 4 && this.status == 200) {
			var linkSpan = document.getElementById("browse-link-"+host)
			linkSpan.innerHTML = "Browsing"
		}
	}
	xmlhttp.open("POST", "/MuWire/Browse", true)
	xmlhttp.setRequestHeader('Content-type', 'application/x-www-form-urlencoded');
	xmlhttp.send("action=browse&host="+host)
}

function refreshStatus() {
	var xmlhttp = new XMLHttpRequest()
	xmlhttp.onreadystatechange = function() {
		if (this.readyState == 4 && this.status == 200) {
			var currentSearch = null
			if (uuid != null)
				currentSearch = statusByUUID.get(uuid)
			statusByUUID.clear()
			
			var activeSearches = this.responseXML.getElementsByTagName("Search")
			var i
			for(i = 0; i < activeSearches.length; i++) {
				var searchStatus = new SearchStatus(activeSearches[i])
				statusByUUID.set(searchStatus.uuid, searchStatus)
			}
			
			
			var table = "<table><thead><tr><th>Query</th><th>Senders</th><th>Results</th></tr></thead><tbody>"
			for (var [searchUUID, status] of statusByUUID) {
				table += "<tr>"
				table += "<td>" + "<a href='#' onclick='refreshGroupBy" + refreshType + "(\"" + searchUUID + "\");return false;'>" + status.query + "</a></td>"
				table += "<td>" + status.senders + "</td>"
				table += "<td>" + status.results + "</td>"
				table += "</tr>"
			}
			table += "</tbody></table>"
			
			var activeDiv = document.getElementById("activeSearches")
			activeDiv.innerHTML = table
			
			if (uuid != null) {
				var newStatus = statusByUUID.get(uuid)
				if (newStatus.revision > currentSearch.revision)
					refreshFunction(uuid)
			}
		}
	}
	xmlhttp.open("GET", "/MuWire/Search?section=status", true)
	xmlhttp.send()
}

var refreshFunction = null
var refreshType = null

function initGroupBySender() {
	refreshFunction = refreshGroupBySender
	refreshType = "Sender"
	setInterval(refreshStatus, 3000);
	setTimeout(refreshStatus, 1);
}

function initGroupByFile() {
	refreshFunction = refreshGroupByFile
	refreshType = "File"
	setInterval ( refreshStatus, 3000);
	setTimeout ( refreshStatus, 1);
}
