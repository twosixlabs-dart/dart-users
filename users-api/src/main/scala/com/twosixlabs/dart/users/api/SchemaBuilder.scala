package com.twosixlabs.dart.users.api

import com.twosixlabs.dart.json.JsonFormat.marshalFrom
import sttp.tapir.SchemaType.{SArray, SObjectInfo, SProduct, SString}
import sttp.tapir.{FieldName, Schema, SchemaType, Validator}

import scala.reflect.{ClassTag, classTag}
import scala.util.matching.Regex

case class SchemaBuilderCommonFields[ T ](
    description : Option[ String ] = None,
    isOptional : Boolean = false,
    deprecated : Boolean = false,
    validators : Seq[ Validator[ T ] ] = Nil,
    enum : Seq[ T ] = Nil,
    example : Option[ T ] = None,
    default : Option[ T ] = None,
    serializer : Option[ T => Any ] = None,
)

trait SchemaBuilder[ T ] {
    private[api] type SelfType

    private[api] val commonFields : SchemaBuilderCommonFields[ T ]

    private[api] def updateCommonFields( fields : SchemaBuilderCommonFields[ T ] ) : SelfType

    private[api] def addValidator( validator : Validator[ T ] ) : SelfType = updateCommonFields( commonFields.copy( validators = validator +: commonFields.validators ) )

    def description( description : String ) : SelfType = updateCommonFields( commonFields.copy( description = Some( description ) ) )
    def optional : SelfType = updateCommonFields( commonFields.copy( isOptional = true ) )
    def deprecated : SelfType = updateCommonFields( commonFields.copy( deprecated = true ) )
    def allowValues( value : T, otherValues : T* ) : SelfType = {
        updateCommonFields( commonFields.copy( enum = commonFields.enum ++ ( value +: otherValues ) ) )
    }
    def example( value : T ) : SelfType = updateCommonFields( commonFields.copy( example = Some( value ) ) )
    def default( value : T ) : SelfType = updateCommonFields( commonFields.copy( default = Some( value ) ) )
    def useSerializer( serializer : T => Any ) : SelfType = updateCommonFields( commonFields.copy( serializer = Some( serializer ) ) )
}

case class SchemaBuilderWithoutType[ T ]( override private[api] val commonFields : SchemaBuilderCommonFields[ T ] = SchemaBuilderCommonFields() ) extends SchemaBuilder[ T ] {
    override private[api] type SelfType = SchemaBuilderWithoutType[ T ]

    override private[api] def updateCommonFields( fields : SchemaBuilderCommonFields[ T ] ) : SelfType = copy( commonFields = fields )
}

trait SchemaBuilderWithType[ T ] extends SchemaBuilder[ T ] {
    private[api] val schemaType : SchemaType

    private[api] def addPrivateFields( partlyBuiltSchema : Schema[ T ] ) : Schema[ T ] = partlyBuiltSchema

    final def build() : Schema[ T ] = {
        val schema = Schema[ T ](
            schemaType = schemaType,
            isOptional = commonFields.isOptional,
            description = commonFields.description,
            deprecated = commonFields.deprecated,
            validator = if ( commonFields.validators.isEmpty ) Validator.pass[ T ] else Validator.all[ T ]( commonFields.validators : _* ),
            default = commonFields.default.map( v => (v, commonFields.serializer.map( s => s( v ) ) ) ),
            encodedExample = commonFields.example.flatMap( v => commonFields.serializer.map( s => s( v ) ) )
        )
        addPrivateFields( schema )
    }
}

case class GenericSchemaBuilder[ T ](
                                      override private[api] val schemaType : SchemaType,
                                      override private[api] val commonFields : SchemaBuilderCommonFields[ T ] = SchemaBuilderCommonFields[ T ]( serializer = Some( ( v : T ) => v.toString ) ),
) extends SchemaBuilderWithType[ T ] {
    override private[api] type SelfType = GenericSchemaBuilder[ T ]

    override private[ api ] def updateCommonFields( fields : SchemaBuilderCommonFields[ T ] ) : GenericSchemaBuilder[T] = {
        copy( commonFields = fields )
    }
}

case class NumericSchemaBuilder[ T: Numeric ](
                                               override private[api] val schemaType : SchemaType,
                                               override private[api] val commonFields : SchemaBuilderCommonFields[ T ] = SchemaBuilderCommonFields[ T ]( serializer = Some( ( v : T) => v ) ),
) extends SchemaBuilderWithType[ T ] {
    override private[api] type SelfType = NumericSchemaBuilder[ T ]
    override private[api] def updateCommonFields( fields : SchemaBuilderCommonFields[ T ] ) : NumericSchemaBuilder[ T ] = copy( commonFields = fields )

    def min( min : T, exclusive : Boolean = false ) : NumericSchemaBuilder[ T ] = addValidator( Validator.min[ T ]( min, exclusive ) )
    def max( max : T, exclusive : Boolean = false ) : NumericSchemaBuilder[ T ] = addValidator( Validator.max[ T ]( max, exclusive ) )
    def range( min : T, max : T, exclusive : Boolean ) : NumericSchemaBuilder[ T ] = {
        addValidator( Validator.min[ T ]( min, exclusive ).and( Validator.min[ T ]( max, exclusive ) ) )
    }
}

case class ArraySchemaBuilder[ C[ _ ] <: Iterable[ _ ], T ](
                                                             override private[api] val schemaType : SchemaType,
                                                             override private[api] val commonFields : SchemaBuilderCommonFields[ C[ T ] ] = SchemaBuilderCommonFields( serializer = Some( marshalFrom( _ ) ) ),
) extends SchemaBuilderWithType[ C[ T ] ] {
    override private[api] type SelfType = ArraySchemaBuilder[ C, T ]
    override private[api] def updateCommonFields( fields : SchemaBuilderCommonFields[ C[ T ] ] ) : ArraySchemaBuilder[ C, T ] = copy( commonFields = fields )



    def elementSchema( childSchema : Schema[ T ] ) : ArraySchemaBuilder[ C, T ] = {
        copy( schemaType = SArray( childSchema ) )
    }

    def minSize( min : Int ) : ArraySchemaBuilder[ C, T ] = addValidator( Validator.minSize[ T, C ]( min ) )
    def maxSize( max : Int ) : ArraySchemaBuilder[ C, T ] = addValidator( Validator.maxSize[ T, C ]( max ) )
    def sizeRange( min : Int, max : Int ) : ArraySchemaBuilder[ C, T ] = {
        minSize( min ).maxSize( max )
    }
}

case class StringSchemaBuilder(
                                private[api] val format : Option[ String ] = None,
                                override private[api] val commonFields : SchemaBuilderCommonFields[ String ] = SchemaBuilderCommonFields( serializer = Some( v => v ) ),
) extends SchemaBuilderWithType[ String ] {
    override private[api] type SelfType = StringSchemaBuilder
    override private[ api ] val schemaType = SString

    def regex( pattern : String ) : StringSchemaBuilder = addValidator( Validator.pattern( pattern ) )
    def regex( pattern : Regex ) : StringSchemaBuilder = addValidator( Validator.pattern( pattern.regex ) )
    def minLength( length : Int ) : StringSchemaBuilder = addValidator( Validator.minLength( length ) )
    def maxLength( length : Int ) : StringSchemaBuilder = addValidator( Validator.maxLength( length ) )
    def lengthRange( min : Int, max : Int ) : StringSchemaBuilder = addValidator( Validator.minLength( min ).and( Validator.maxLength( max ) ) )

    def format( newFormat : String ) : StringSchemaBuilder = copy( format = Some( newFormat ) )
    def date : StringSchemaBuilder = format( "date" )
    def dateTime : StringSchemaBuilder = format( "date-time" )
    def password : StringSchemaBuilder = format( "password" )
    def byte : StringSchemaBuilder = format( "byte" )
    def binary : StringSchemaBuilder = format( "binary" )

    override private[ api ] def updateCommonFields( fields : SchemaBuilderCommonFields[ String ] ) : StringSchemaBuilder = {
        copy( commonFields = fields )
    }

    override private[ api ] def addPrivateFields( partlyBuiltSchema : Schema[ String ] ) : Schema[ String ] = {
        partlyBuiltSchema.copy( format = format )
    }
}

case class ObjectSchemaBuilder[ T : ClassTag ](
                                                private[api] val fields : Map[ String, Schema[ _ ] ],
                                                override private[api] val commonFields : SchemaBuilderCommonFields[ T ] = SchemaBuilderCommonFields( serializer = Some( marshalFrom( _ ) ) ),
) extends SchemaBuilderWithType[ T ] {
    override private[api] type SelfType = ObjectSchemaBuilder[ T ]
    override private[api] def updateCommonFields( fields : SchemaBuilderCommonFields[ T ] ) : ObjectSchemaBuilder[ T ] = copy( commonFields = fields )

    override private[ api ] val schemaType = SProduct( SObjectInfo( classTag[ T ].runtimeClass.getSimpleName ), fields.map {
        case (key, value) => FieldName( key, key ) -> value
    } )

    def addField( field : (String, Schema[ _ ]) ) : ObjectSchemaBuilder[ T ] = copy( fields + field )
}

object SchemaBuilder {
    def forType[ T ] : SchemaBuilderWithoutType[ T ] = SchemaBuilderWithoutType[ T ]( SchemaBuilderCommonFields() )
    def string : StringSchemaBuilder = StringSchemaBuilder( commonFields = SchemaBuilderCommonFields() )
    def boolean : GenericSchemaBuilder[ Boolean ] = GenericSchemaBuilder( SchemaType.SBoolean, SchemaBuilderCommonFields() )
    def integer : NumericSchemaBuilder[ Int ] = NumericSchemaBuilder( SchemaType.SInteger, SchemaBuilderCommonFields() )
    def double : NumericSchemaBuilder[ Double ] = NumericSchemaBuilder( SchemaType.SNumber, SchemaBuilderCommonFields() )
    def arrayOf[ C[ _ ] <: Iterable[ _ ], T ]( eleSchema : Schema[ T ]) : ArraySchemaBuilder[ C, T ] = {
        ArraySchemaBuilder[ C, T ]( SchemaType.SArray( eleSchema ), SchemaBuilderCommonFields[ C[ T ] ]() )
    }
    def obj[ T : ClassTag ]( fields : (String, Schema[ _ ])* ) : ObjectSchemaBuilder[ T ] = ObjectSchemaBuilder[ T ]( fields.toMap, SchemaBuilderCommonFields() )
    def obj[ T : ClassTag ] : ObjectSchemaBuilder[ T ] = obj[ T ]()

}
