package ics205.util
sealed trait AppExceptions

class UnauthorizedException extends Exception("You do not have permission to edit plans") with AppExceptions

