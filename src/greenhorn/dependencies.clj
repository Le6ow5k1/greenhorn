(ns greenhorn.dependencies)

(defprotocol Dependency
  (name [d])
  (version [d])
  (revision [d]))

(defprotocol DependencyDiff
  (name [d])
  (added? [d])
  (removed? [d]))
