package com.solesonic.agent.image;

/**
 * What the user wants done, decided before any workflow is chosen. Each stored workflow lists the
 * intents it serves in {@code comfy_workflow_intent}, so the routing model answers one narrow
 * question instead of comparing every workflow's prose description.
 */
public enum ImageIntent {

    /**
     * No photo is attached: generate a brand new image from the request alone.
     */
    NEW_IMAGE,

    /**
     * Redraw the attached photo in a new style, medium, lighting or mood, keeping its composition.
     */
    RESTYLE_PHOTO,

    /**
     * Keep the person in the attached photo and show them somewhere else, doing something else, or
     * dressed differently.
     */
    EDIT_PERSON_IN_PHOTO
}
