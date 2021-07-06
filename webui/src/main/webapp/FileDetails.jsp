<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@ page import="com.muwire.webui.*" %>
<%@ page import="java.io.*" %>
<%@include file="initcode.jsi"%>

<% 

String pagetitle=Util._t("File Details"); 
String helptext = Util._t("View details about the selected shared file here.");

String path = request.getParameter("path");
File file = Util.getFromPathElements(path);
String filePath = Util.escapeHTMLinXML(file.getAbsolutePath());
%>

<html>
	<head>
<%@ include file="css.jsi"%>
<script src="js/fileDetails.js?<%=version%>" type="text/javascript"></script>

<script nonce="<%=cspNonce%>" type="text/javascript">
	path="<%=path%>"
</script>

	</head>
	<body>
<%@ include file="header.jsi"%>
	    <aside>
		<div class="menubox-divider"></div>
<%@include file="sidebar.jsi"%>    	
	    </aside>
	    <section class="main foldermain">
	    	<h2><%=Util._t("Details for {0}", filePath)%></h2>
	    	<h3><%=Util._t("Search Hits")%></h3>
		    <div id="table-wrapper">
				<div id="table-scroll">
					<div id="hitsTable"></div>
				</div>
			</div>
			<hr/>
			<h3><%=Util._t("Downloaders")%></h3>
			<div id="table-wrapper">
				<div id="table-scroll">
					<div id="downloadersTable"></div>
				</div>
			</div>
			<hr/>
			<h3><%=Util._t("Certificates")%></h3>
			<div id="table-wrapper">
				<div id="table-scroll">
					<div id="certificatesTable"></div>
				</div>
			</div>
	    </section>
	</body>
</html>
