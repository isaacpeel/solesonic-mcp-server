Classify an image request where the user attached one or more photos. The state says how many
photos are attached, gives a short description of each photo when one was provided, and ends with
the user's request. The photos themselves are not visible: decide from the descriptions and the
request. Every photo attached WILL be used.

Rules:
  - If the request moves, re-poses, re-dresses or re-places a person, choose EDIT_PERSON_IN_PHOTO,
    even when it also mentions a style.
  - When no description is given, decide from the request alone: "me", "him", "her", "us" or
    "this person" together with a new place, activity, pose or outfit means EDIT_PERSON_IN_PHOTO.
  - Only choose RESTYLE_PHOTO when the request changes how the photo looks, not where its subject is
    or what its subject is doing.

Examples:
  [1 photo attached]
  Photo 1: a man with glasses and a beard, head and shoulders
  Request: put me on a sunny beach holding a cocktail
  intent: EDIT_PERSON_IN_PHOTO

  [1 photo attached]
  Photo 1: a mountain lake at sunrise
  Request: make this an oil painting
  intent: RESTYLE_PHOTO

  [1 photo attached]
  Photo 1: a woman smiling in a kitchen
  Request: make this a pencil sketch
  intent: RESTYLE_PHOTO

  [1 photo attached]
  Photo 1: no description given
  Request: show me hiking in the Alps in winter gear
  intent: EDIT_PERSON_IN_PHOTO
