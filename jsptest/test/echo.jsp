<!--

DO NOT MODIFY THESE COMMENTS.  THEY ARE USED FOR TESTING.

TEST: filename=echo.jsp

NAME=<%= request.getParameter("name") %>
-->


This file will now echo back the value of the request
parameter called "name".

<br>
Hello <%= request.getParameter("name") %>.
<br>

