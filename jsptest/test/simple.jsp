<!--

DO NOT MODIFY THESE COMMENTS.  THEY ARE USED FOR TESTING.

TEST: filename=simple.jsp

-->


<h2>This is a simple test JSP file</h2>

This file will generate at least one cookie.
<%

	response.addCookie(new Cookie("test_name", "test_value"));


%>
