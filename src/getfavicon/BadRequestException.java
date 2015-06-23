package getfavicon;

import utils.ExternalException;

/**
* User: And390
* Date: 17.06.15
* Time: 0:49
*/
public class BadRequestException extends ExternalException
{
    public BadRequestException()  {}

    public BadRequestException(String message)  {  super(message);  }

    public BadRequestException(String message, Throwable cause)  {  super(message, cause);  }

    public BadRequestException(Throwable cause)  {  super(cause);  }
}
