package com.atomist.rug.runtime

import javax.script.ScriptContext

import com.atomist.param.{Parameter, Tag}
import com.atomist.project.ProjectOperation
import com.atomist.project.common.support.ProjectOperationParameterSupport
import com.atomist.source.ArtifactSource
import com.typesafe.scalalogging.LazyLogging
import jdk.nashorn.api.scripting.ScriptObjectMirror

import scala.util.Try
import scala.collection.JavaConverters._

/**
  * Superclass for all operations that delegate to JavaScript
  *
  * @param jsc       JavaScript context
  * @param className name of the class
  * @param jsVar     var reference in Nashorn
  * @param rugAs     backing artifact source for the Rug archive
  */
abstract class JavaScriptInvokingProjectOperation(
                                                   jsc: JavaScriptContext,
                                                   className: String,
                                                   jsVar: ScriptObjectMirror,
                                                   rugAs: ArtifactSource
                                                 )
  extends ProjectOperationParameterSupport
    with ContextAwareProjectOperation
    with LazyLogging {

  readTagsFromMetadata.foreach(t => addTag(t))

  readParametersFromMetadata.foreach(p => addParameter(p))

  protected var _context: Seq[ProjectOperation] = Nil

  override def setContext(ctx: Seq[ProjectOperation]): Unit = {
    _context = ctx
  }

  protected def context: Seq[ProjectOperation] = _context

  override def description: String = jsc.engine.invokeFunction("get_metadata", jsVar, "editor-description") match {
    case s: String => s
    case _ => name
  }

  protected def readTagsFromMetadata: Seq[Tag] = {
    Try {
      jsc.engine.invokeFunction("get_metadata", jsVar, "tags") match {
        case som: ScriptObjectMirror =>
          val stringValues = som.values().asScala collect {
            case s: String => s
          }
          stringValues.map(s => Tag(s, s)).toSeq
        case _ => Nil
      }
    }.getOrElse(Nil)
  }

  protected def readParametersFromMetadata: Seq[Parameter] = {
    Try {
      val vars = jsc.engine.getContext.getBindings(ScriptContext.ENGINE_SCOPE)
      val pclass = jsc.engine.invokeFunction("get_metadata", jsVar, "parameter-class").asInstanceOf[String]
      jsc.engine.invokeFunction("get_metadata", vars.get(pclass).asInstanceOf[ScriptObjectMirror], "params") match {
        case som: ScriptObjectMirror =>
          val values = som.asScala collect {
            case (name: String, details: AnyRef) => {
              //TODO - can we do some fancy data binding here? map keys match setters (mostly)
              val p = Parameter(name, details.asInstanceOf[ScriptObjectMirror].get("pattern").asInstanceOf[String])
              p.setDisplayName(details.asInstanceOf[ScriptObjectMirror].get("displayName").asInstanceOf[String])
              p.setMaxLength(details.asInstanceOf[ScriptObjectMirror].get("maxLength").asInstanceOf[Int])
              p.setMinLength(details.asInstanceOf[ScriptObjectMirror].get("minLength").asInstanceOf[Int])
              p.setDefaultRef(details.asInstanceOf[ScriptObjectMirror].get("defaultRef").asInstanceOf[String])
              p.setDisplayable(details.asInstanceOf[ScriptObjectMirror].get("displayable").asInstanceOf[Boolean])
              p.setRequired(details.asInstanceOf[ScriptObjectMirror].get("required").asInstanceOf[Boolean])
              p.setDefaultValue(details.asInstanceOf[ScriptObjectMirror].get("defaultValue").asInstanceOf[String])
              p.setValidInputDescription(details.asInstanceOf[ScriptObjectMirror].get("validInputDescription").asInstanceOf[String])
              p.describedAs(details.asInstanceOf[ScriptObjectMirror].get("description").asInstanceOf[String])
              //TODO it's unclear what allowedValues is for given an AllowedValue is just a name/display_name mapping
              //p.setAllowedValues()
              p
            }
          }
          values.toSeq
        case _ => Nil
      }
    }.getOrElse(Nil)
  }

}