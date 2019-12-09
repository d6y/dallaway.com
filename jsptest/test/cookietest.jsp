
<!--

DO NOT MODIFY THESE COMMENTS.  THEY ARE USED FOR TESTING.

TEST: filename=cookietest.jsp

-->


<h2>This is a test of cookie sending from JSPTest Session object</h2>


<%

	
	String echo_value = "no cookies found";

	// We look for a cookie sent to us called "test" and send
	// the value back as a cookie called "echo"
	
	
	
	Cookie[] cookieList = request.getCookies();
        if (cookieList!=null)
	{
		
		echo_value = "cookie test not found";
		
		for (int l=0;l<cookieList.length;l++)
	        {
	        	%> Looking at cookie: <%=cookieList[l].getName()%> = <%=cookieList[l].getValue()%><br> <%
	         	if( cookieList[l].getName().equals("test") ) 
	                {
                    		echo_value = cookieList[l].getValue();
                    		break;
                    	}
                 }
        }
	
	
		
	response.addCookie(new Cookie("echo", echo_value));


%>
