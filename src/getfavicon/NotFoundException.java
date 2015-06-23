package getfavicon;

import utils.ExternalException;

/**
* User: And390
* Date: 17.06.15
* Time: 0:48
*/
public class NotFoundException extends ExternalException
{
    public NotFoundException()  {}

    public NotFoundException(String message)  {  super(message);  }

    public NotFoundException(String message, Throwable cause)  {  super(message, cause);  }

    public NotFoundException(Throwable cause)  {  super(cause);  }

}
