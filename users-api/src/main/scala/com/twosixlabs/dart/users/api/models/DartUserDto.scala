package com.twosixlabs.dart.users.api.models

import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.{JsonIgnore, JsonInclude, JsonProperty}
import com.twosixlabs.dart.auth.groups.DartGroup
import com.twosixlabs.dart.auth.user.{DartUser, DartUserInfo, UserMod}
import com.twosixlabs.dart.users.api.SchemaBuilder

import scala.beans.BeanProperty

@JsonInclude( Include.NON_EMPTY )
case class DartUserForAddDto(
    @BeanProperty @JsonProperty( "first_name" ) firstName : Option[ String ],
    @BeanProperty @JsonProperty( "last_name" ) lastName : Option[ String ],
    @BeanProperty @JsonProperty( "email" ) email : Option[ String ],
    @BeanProperty @JsonProperty( "groups" ) groups : Option[ Set[ String ] ],
    @BeanProperty @JsonProperty( "password" ) password : Option[ String ],
) {
    @JsonIgnore
    def toUserMod( userName : String ) : UserMod = UserMod( DartUser(
        userName,
        groups.map( _.map( DartGroup.fromString ) ).getOrElse( Set.empty ),
        DartUserInfo(
            firstName,
            lastName,
            email,
        )
    ), password )
}

object DartUserForAddDto {
    def fromDartUser( user: DartUser ) : DartUserForAddDto = DartUserForAddDto(
        user.userInfo.firstName,
        user.userInfo.lastName,
        user.userInfo.email,
        Some( user.groups.map( _.toString ) ),
        None,
    )

    implicit class DartUserWithAddDto( dartUser : DartUser ) {
        def toAddDto : DartUserForAddDto = fromDartUser( dartUser )
        def toAddDto( password : String ) : DartUserForAddDto =
            fromDartUser( dartUser ).copy( password = Some( password ) )
    }

    private[api] lazy val schema  = {
        SchemaBuilder
          .obj[ DartUserForAddDto ]
          .description( "Object containing user information and groups" )
          .addField( "first_name" -> SchemaBuilder.string.optional.regex( """[a-zA-Z]+""".r ).example( "John" ).build() )
          .addField( "last_name" -> SchemaBuilder.string.optional.regex( """[a-zA-Z]+""".r ).example( "Smith" ).build() )
          .addField( "email" -> SchemaBuilder.string.optional.regex( """([^@])+@([^\.]+)\.([a-zA-Z]+)]""".r ).example( "john.smith@website.com" ).build() )
          .addField( "groups" -> SchemaBuilder.arrayOf[ Set, String ](
              SchemaBuilder.string.regex( s"${DartGroup.GroupPattern}|${DartGroup.ProgramManagerPattern}" )
                .example( "global-member" ).build() ).build() )
          .addField( "password" -> SchemaBuilder.string.optional.build() )
          .build()
    }
}

@JsonInclude( Include.NON_EMPTY )
case class DartUserDto(
    @BeanProperty @JsonProperty( "user_name" ) userName : String,
    @BeanProperty @JsonProperty( "first_name" ) firstName : Option[ String ],
    @BeanProperty @JsonProperty( "last_name" ) lastName : Option[ String ],
    @BeanProperty @JsonProperty( "email" ) email : Option[ String ],
    @BeanProperty @JsonProperty( "groups" ) groups : Set[ String ],
) {
    @JsonIgnore
    def toDartUser : DartUser = DartUser(
        userName,
        groups.map( DartGroup.fromString ),
        DartUserInfo(
            firstName,
            lastName,
            email,
        )
    )
}

object DartUserDto {
    def fromDartUser( dartUser : DartUser ) : DartUserDto = {
        DartUserDto(
            dartUser.userName,
            dartUser.userInfo.firstName,
            dartUser.userInfo.lastName,
            dartUser.userInfo.email,
            dartUser.groups.map( _.toString )
        )
    }

    implicit class DartUserWithDto( dartUser : DartUser ) {
        def toDto : DartUserDto = fromDartUser( dartUser )
    }

    private[api] lazy val schema  = {
        SchemaBuilder
          .obj[ DartUserDto ]
          .description( "Object containing user information and groups" )
          .addField( "user_name" -> SchemaBuilder.string.regex( """[a-zA-Z\-]""" ).example( "john-smith-1" ).build() )
          .addField( "first_name" -> SchemaBuilder.string.optional.regex( """[a-zA-Z]+""".r ).example( "John" ).build() )
          .addField( "last_name" -> SchemaBuilder.string.optional.regex( """[a-zA-Z]+""".r ).example( "Smith" ).build() )
          .addField( "email" -> SchemaBuilder.string.optional.regex( """([^@])+@([^\.]+)\.([a-zA-Z]+)]""".r ).example( "john.smith@website.com" ).build() )
          .addField( "groups" -> SchemaBuilder.arrayOf[ Set, String ](
              SchemaBuilder.string.regex( s"${DartGroup.GroupPattern}|${DartGroup.ProgramManagerPattern}" )
                .example( "global-member" ).build() ).build() )
          .build()

    }
}
